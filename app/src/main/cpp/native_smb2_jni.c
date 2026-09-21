#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <smb2/smb2.h>
#include <smb2/libsmb2.h>

#define LOG_TAG "Vr2xrNativeSmb2"

struct native_smb_file {
    struct smb2_context *context;
    struct smb2fh *file;
    uint64_t size;
    int connected;
    pthread_mutex_t mutex;
    uint8_t *buffer;
    size_t buffer_capacity;
};

struct parsed_smb_uri {
    char *host;
    char *share;
    char *path;
};

static void throw_io_exception(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/io/IOException");
    if (exception_class != NULL) {
        (*env)->ThrowNew(env, exception_class, message != NULL ? message : "Native SMB2 error");
    }
}

static int hex_value(char value) {
    if (value >= '0' && value <= '9') return value - '0';
    if (value >= 'a' && value <= 'f') return value - 'a' + 10;
    if (value >= 'A' && value <= 'F') return value - 'A' + 10;
    return -1;
}

static char *percent_decode(const char *value, size_t length) {
    char *decoded = calloc(length + 1, 1);
    if (decoded == NULL) return NULL;
    size_t source = 0;
    size_t target = 0;
    while (source < length) {
        if (value[source] == '%' && source + 2 < length) {
            int high = hex_value(value[source + 1]);
            int low = hex_value(value[source + 2]);
            if (high >= 0 && low >= 0) {
                decoded[target++] = (char) ((high << 4) | low);
                source += 3;
                continue;
            }
        }
        decoded[target++] = value[source++];
    }
    decoded[target] = '\0';
    return decoded;
}

static void free_parsed_uri(struct parsed_smb_uri *parsed) {
    if (parsed == NULL) return;
    free(parsed->host);
    free(parsed->share);
    free(parsed->path);
    memset(parsed, 0, sizeof(*parsed));
}

static int parse_smb_uri(const char *uri, struct parsed_smb_uri *parsed) {
    const char prefix[] = "smb://";
    if (uri == NULL || strncmp(uri, prefix, sizeof(prefix) - 1) != 0) return -1;
    const char *host_start = uri + sizeof(prefix) - 1;
    const char *share_separator = strchr(host_start, '/');
    if (share_separator == NULL || share_separator == host_start) return -1;
    const char *share_start = share_separator + 1;
    const char *path_separator = strchr(share_start, '/');
    const char *share_end = path_separator != NULL ? path_separator : uri + strlen(uri);
    if (share_end == share_start) return -1;

    parsed->host = percent_decode(host_start, (size_t) (share_separator - host_start));
    parsed->share = percent_decode(share_start, (size_t) (share_end - share_start));
    parsed->path = path_separator != NULL
        ? percent_decode(path_separator + 1, strlen(path_separator + 1))
        : strdup("");
    if (parsed->host == NULL || parsed->share == NULL || parsed->path == NULL) {
        free_parsed_uri(parsed);
        return -1;
    }
    return 0;
}

static const char *safe_utf(JNIEnv *env, jstring value, const char **chars) {
    if (value == NULL) {
        *chars = "";
        return *chars;
    }
    *chars = (*env)->GetStringUTFChars(env, value, NULL);
    return *chars;
}

static void release_utf(JNIEnv *env, jstring value, const char *chars) {
    if (value != NULL && chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, value, chars);
    }
}

static void close_native_file(struct native_smb_file *handle) {
    if (handle == NULL) return;
    if (handle->file != NULL && handle->context != NULL) {
        smb2_close(handle->context, handle->file);
        handle->file = NULL;
    }
    if (handle->connected && handle->context != NULL) {
        smb2_disconnect_share(handle->context);
        handle->connected = 0;
    }
    if (handle->context != NULL) {
        smb2_destroy_context(handle->context);
        handle->context = NULL;
    }
    free(handle->buffer);
    pthread_mutex_destroy(&handle->mutex);
    free(handle);
}

JNIEXPORT jlong JNICALL
Java_com_vr2xr_smb_NativeSmbRandomAccessSource_nativeOpen(
        JNIEnv *env,
        jobject instance,
        jstring uri_value,
        jstring domain_value,
        jstring username_value,
        jstring password_value) {
    (void) instance;
    const char *uri = NULL;
    const char *domain = NULL;
    const char *username = NULL;
    const char *password = NULL;
    struct parsed_smb_uri parsed = {0};
    struct native_smb_file *handle = NULL;
    char error_message[512];

    if (safe_utf(env, uri_value, &uri) == NULL ||
        safe_utf(env, domain_value, &domain) == NULL ||
        safe_utf(env, username_value, &username) == NULL ||
        safe_utf(env, password_value, &password) == NULL) {
        throw_io_exception(env, "Native SMB2 string conversion failed");
        goto cleanup;
    }
    if (parse_smb_uri(uri, &parsed) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Native SMB2 URL parsing failed");
        throw_io_exception(env, "Native SMB2 URL parsing failed");
        goto cleanup;
    }

    handle = calloc(1, sizeof(*handle));
    if (handle == NULL || pthread_mutex_init(&handle->mutex, NULL) != 0) {
        throw_io_exception(env, "Native SMB2 allocation failed");
        free(handle);
        handle = NULL;
        goto cleanup;
    }
    handle->context = smb2_init_context();
    if (handle->context == NULL) {
        throw_io_exception(env, "Native SMB2 context creation failed");
        goto failure;
    }
    smb2_set_timeout(handle->context, 10);
    smb2_set_user(handle->context, username);
    smb2_set_password(handle->context, password);
    smb2_set_domain(handle->context, domain);

    if (smb2_connect_share(handle->context, parsed.host, parsed.share, username) != 0) {
        snprintf(error_message, sizeof(error_message), "Native SMB2 share connection failed: %s",
                 smb2_get_error(handle->context));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", error_message);
        throw_io_exception(env, error_message);
        goto failure;
    }
    handle->connected = 1;
    handle->file = smb2_open(handle->context, parsed.path, O_RDONLY);
    if (handle->file == NULL) {
        snprintf(error_message, sizeof(error_message), "Native SMB2 file open failed: %s",
                 smb2_get_error(handle->context));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", error_message);
        throw_io_exception(env, error_message);
        goto failure;
    }
    struct smb2_stat_64 stat_value;
    memset(&stat_value, 0, sizeof(stat_value));
    if (smb2_fstat(handle->context, handle->file, &stat_value) != 0) {
        snprintf(error_message, sizeof(error_message), "Native SMB2 attribute read failed: %s",
                 smb2_get_error(handle->context));
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", error_message);
        throw_io_exception(env, error_message);
        goto failure;
    }
    handle->size = stat_value.smb2_size;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Native libsmb2 video source opened");

    free_parsed_uri(&parsed);
    release_utf(env, uri_value, uri);
    release_utf(env, domain_value, domain);
    release_utf(env, username_value, username);
    release_utf(env, password_value, password);
    return (jlong) (intptr_t) handle;

failure:
    close_native_file(handle);
    handle = NULL;
cleanup:
    free_parsed_uri(&parsed);
    release_utf(env, uri_value, uri);
    release_utf(env, domain_value, domain);
    release_utf(env, username_value, username);
    release_utf(env, password_value, password);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_vr2xr_smb_NativeSmbRandomAccessSource_nativeSize(
        JNIEnv *env, jobject instance, jlong pointer) {
    (void) env;
    (void) instance;
    struct native_smb_file *handle = (struct native_smb_file *) (intptr_t) pointer;
    return handle != NULL ? (jlong) handle->size : -1;
}

JNIEXPORT jint JNICALL
Java_com_vr2xr_smb_NativeSmbRandomAccessSource_nativeReadAt(
        JNIEnv *env,
        jobject instance,
        jlong pointer,
        jlong position,
        jbyteArray target,
        jint offset,
        jint length) {
    (void) instance;
    struct native_smb_file *handle = (struct native_smb_file *) (intptr_t) pointer;
    if (handle == NULL || target == NULL || position < 0 || offset < 0 || length < 0 ||
        (jlong) offset + length > (*env)->GetArrayLength(env, target)) {
        throw_io_exception(env, "Invalid native SMB2 read request");
        return -1;
    }
    if (position >= (jlong) handle->size) return -1;
    uint32_t requested = (uint32_t) length;
    uint64_t remaining = handle->size - (uint64_t) position;
    if ((uint64_t) requested > remaining) requested = (uint32_t) remaining;

    pthread_mutex_lock(&handle->mutex);
    if (handle->buffer_capacity < requested) {
        uint8_t *resized = realloc(handle->buffer, requested);
        if (resized == NULL) {
            pthread_mutex_unlock(&handle->mutex);
            throw_io_exception(env, "Native SMB2 read buffer allocation failed");
            return -1;
        }
        handle->buffer = resized;
        handle->buffer_capacity = requested;
    }
    int result = smb2_pread(
        handle->context,
        handle->file,
        handle->buffer,
        requested,
        (uint64_t) position
    );
    if (result >= 0) {
        (*env)->SetByteArrayRegion(env, target, offset, result, (const jbyte *) handle->buffer);
    }
    pthread_mutex_unlock(&handle->mutex);
    if (result < 0) {
        char error_message[512];
        snprintf(error_message, sizeof(error_message), "Native SMB2 read failed: %s",
                 smb2_get_error(handle->context));
        throw_io_exception(env, error_message);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_vr2xr_smb_NativeSmbRandomAccessSource_nativeClose(
        JNIEnv *env, jobject instance, jlong pointer) {
    (void) env;
    (void) instance;
    close_native_file((struct native_smb_file *) (intptr_t) pointer);
}

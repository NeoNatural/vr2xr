cmake_minimum_required(VERSION 3.22.1)
project(vr2xr_smb LANGUAGES C)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ENABLE_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ENABLE_LIBKRB5 OFF CACHE BOOL "" FORCE)
set(ENABLE_GSSAPI OFF CACHE BOOL "" FORCE)
set(ENABLE_LIBDCERPC OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/libsmb2 libsmb2-build EXCLUDE_FROM_ALL)

add_library(smb2-jni SHARED native_smb2_jni.c)
target_compile_options(smb2-jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(smb2-jni PRIVATE smb2 log)
target_include_directories(
    smb2-jni
    PRIVATE third_party/libsmb2/include
)

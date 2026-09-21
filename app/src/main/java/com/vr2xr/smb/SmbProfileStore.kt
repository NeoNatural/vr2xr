package com.vr2xr.smb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SmbProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun loadAll(): List<SmbProfile> {
        val raw = preferences.getString(KEY_PROFILES, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val profile = runCatching { decodeProfile(array.getJSONObject(index)) }.getOrNull()
                if (profile != null) add(profile)
            }
        }
    }

    @Synchronized
    fun find(id: String): SmbProfile? = loadAll().firstOrNull { it.id == id }

    @Synchronized
    fun save(profile: SmbProfile) {
        val profiles = loadAll().filterNot { it.id == profile.id } + profile
        val array = JSONArray()
        profiles.forEach { array.put(encodeProfile(it)) }
        check(preferences.edit().putString(KEY_PROFILES, array.toString()).commit()) {
            "Unable to save SMB account"
        }
    }

    @Synchronized
    fun remove(id: String) {
        val array = JSONArray()
        loadAll().filterNot { it.id == id }.forEach { array.put(encodeProfile(it)) }
        check(preferences.edit().putString(KEY_PROFILES, array.toString()).commit()) {
            "Unable to remove SMB account"
        }
    }

    private fun encodeProfile(profile: SmbProfile): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("host", profile.host)
        .put("share", profile.share)
        .put("domain", profile.domain)
        .put("username", profile.username)
        .put("password", encrypt(profile.password))

    private fun decodeProfile(json: JSONObject): SmbProfile = SmbProfile(
        id = json.getString("id"),
        host = json.getString("host"),
        share = json.getString("share"),
        domain = json.optString("domain"),
        username = json.optString("username"),
        password = decrypt(json.getString("password"))
    )

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$ciphertext"
    }

    private fun decrypt(encoded: String): String {
        val separator = encoded.indexOf(':')
        require(separator > 0) { "Invalid encrypted SMB password" }
        val iv = Base64.decode(encoded.substring(0, separator), Base64.NO_WRAP)
        val ciphertext = Base64.decode(encoded.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val PREFERENCES_NAME = "smb_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "vr2xr_smb_profiles_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

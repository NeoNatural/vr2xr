package com.vr2xr.smb

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class SmbProfile(
    val id: String,
    val host: String,
    val share: String,
    val domain: String,
    val username: String,
    val password: String
) {
    val displayName: String
        get() = if (username.isBlank()) "$host/$share" else "$username@$host/$share"

    companion object {
        fun create(
            host: String,
            share: String,
            domain: String,
            username: String,
            password: String
        ): SmbProfile {
            val normalizedHost = host.trim()
            val normalizedShare = share.trim()
            val normalizedDomain = domain.trim()
            val normalizedUsername = username.trim()
            val identity = listOf(
                normalizedHost.lowercase(),
                normalizedShare.lowercase(),
                normalizedDomain.lowercase(),
                normalizedUsername.lowercase()
            ).joinToString("\u0000")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(StandardCharsets.UTF_8))
                .take(16)
                .joinToString("") { "%02x".format(it) }
            return SmbProfile(
                id = digest,
                host = normalizedHost,
                share = normalizedShare,
                domain = normalizedDomain,
                username = normalizedUsername,
                password = password
            )
        }
    }
}

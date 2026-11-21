package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityHashUtils {

    private val secureRandom = SecureRandom()

    fun generateSaltHex(byteCount: Int = 16): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPasscode(saltHex: String, passcode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltHex:$passcode".toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPasscode(saltHex: String, passcode: String, expectedHashHex: String): Boolean {
        if (saltHex.isBlank() || expectedHashHex.isBlank()) return false
        val actual = hashPasscode(saltHex, passcode)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }
}

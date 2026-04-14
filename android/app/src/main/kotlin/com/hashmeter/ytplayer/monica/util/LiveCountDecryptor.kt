package com.hashmeter.ytplayer.monica.util

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object LiveCountDecryptor {
    private const val TAG = "LiveCountDecryptor"
    private const val ENCRYPTION_KEY = "81f483f800328c8e61deaef1734bd2d6b465200da9f4a6fd50bf7961b724d947" // Match server key

    fun decrypt(encryptedData: String): String {
        Log.d(TAG, "🔐 Starting decryption")
        Log.d(TAG, "Input data: $encryptedData")

        // Base64 디코딩
        val data = Base64.decode(encryptedData, Base64.DEFAULT)
        Log.d(TAG, "Base64 decoded size: ${data.size} bytes")

        // IV (처음 16 bytes)와 암호화된 데이터 분리
        val iv = data.copyOfRange(0, 16)
        val encrypted = data.copyOfRange(16, data.size)

        // Prepare 32-byte key for AES-256 (trim to 32 bytes if longer)
        val keyBytes = ENCRYPTION_KEY.toByteArray(Charsets.UTF_8)
        val key32Bytes = keyBytes.copyOf(32) // Take first 32 bytes, pad with zeros if shorter

        // AES-256-CBC 복호화
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key32Bytes, "AES")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted)
    }
}

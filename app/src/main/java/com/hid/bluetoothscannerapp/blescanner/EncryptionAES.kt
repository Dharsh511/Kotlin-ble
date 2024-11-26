package com.hid.bluetoothscannerapp.blescanner

import android.util.Log
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class EncryptionAES {

    // Method to encrypt ID Token with PKCS5 padding
    fun encryptIDTokenWithCustomKey(idToken: String, claimName: String): String {
        try {
            // Extract the AES key from the ID token claim
            val decodedJWT = com.auth0.android.jwt.JWT(idToken)
            val base64EncodedKey = decodedJWT.getClaim(claimName).asString()

            if (base64EncodedKey.isNullOrEmpty()) {
                Log.e("EncryptionAES", "Claim '$claimName' is missing or empty in the JWT.")
                throw IllegalArgumentException("Invalid or missing claim: $claimName")
            }

            Log.d("DecodedClaim", "Base64 Encoded Key from Claim: $base64EncodedKey")

            // Decode Base64 key
            val aesKeyBytes = Base64.getDecoder().decode(base64EncodedKey)
            Log.d("AESKey", "Decoded AES Key (hex): ${aesKeyBytes.joinToString("") { String.format("%02X", it) }}")
            Log.d("AESKeyLength", "AES Key length: ${aesKeyBytes.size} bytes")

            if (aesKeyBytes.size !in listOf(16, 24, 32)) {
                Log.e("EncryptionAES", "Invalid AES key length: ${aesKeyBytes.size}")
                throw IllegalArgumentException("AES key length must be 16, 24, or 32 bytes.")
            }

            val aesKey = SecretKeySpec(aesKeyBytes, "AES")

            // Prepare cipher for AES/ECB/PKCS5Padding
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey)

            // Convert plaintext to bytes
            val plaintextBytes = idToken.toByteArray(Charsets.UTF_8)
            Log.d("Plaintext", "Plaintext: $idToken")
            Log.d("PlaintextHex", "Plaintext (hex): ${plaintextBytes.joinToString("") { String.format("%02X", it) }}")


            // Encrypt the plaintext (Cipher will handle PKCS5 padding automatically)
            val encryptedData = cipher.doFinal(plaintextBytes)
            Log.d("EncryptedDataHex", "Encrypted Data (hex): ${encryptedData.joinToString("") { String.format("%02X", it) }}")
            Log.d("EncryptedDataLength", "Encrypted data length: ${encryptedData.size} bytes")

            // Base64 encode the encrypted data
            val base64EncodedEncryptedData = Base64.getEncoder().encodeToString(encryptedData)
            Log.d("Base64Ciphertext", "Base64 Encrypted Data: $base64EncodedEncryptedData")

            return base64EncodedEncryptedData
        } catch (e: Exception) {
            Log.e("EncryptionAES", "Error during encryption: ${e.message}", e)
            throw RuntimeException("Error during encryption", e)
        }
    }

}

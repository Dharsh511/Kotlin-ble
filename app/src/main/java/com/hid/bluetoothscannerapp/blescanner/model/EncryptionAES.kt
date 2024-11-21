package com.hid.bluetoothscannerapp.blescanner.model

import android.util.Log
import com.auth0.android.jwt.JWT
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class EncryptionAES {

    private fun padAnSix923(data: ByteArray, blockSize: Int): ByteArray {
        val paddingLength = blockSize - (data.size % blockSize)
        val paddingBytes = ByteArray(paddingLength)
        paddingBytes[paddingLength - 1] = paddingLength.toByte()

        Log.d("PaddingAES", "Padded data: ${data.joinToString(", ")}")
        Log.d("PaddingAES", "Padding length: $paddingLength")
        return data + paddingBytes
    }



    fun encryptIDTokenWithCustomKey(idToken: String, claimName: String): String {

        val decodedJWT = JWT(idToken)
        val base64EncodedKey = decodedJWT.getClaim(claimName).asString()


        val aesKeyBytes = Base64.getDecoder().decode(base64EncodedKey)
        Log.d("keyBytes", "keyasRawbytes: ${aesKeyBytes}")
        Log.d("lengthKeybytes", "length is: ${aesKeyBytes.size}")
        if (aesKeyBytes.size != 16 && aesKeyBytes.size != 24 && aesKeyBytes.size != 32) {
            throw IllegalArgumentException("Invalid AES key length: ${aesKeyBytes.size}. Expected 16, 24, or 32 bytes.")
        }


        val aesKey = SecretKeySpec(aesKeyBytes, "AES")


        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)


        val paddedData = padAnSix923(idToken.toByteArray(), 16)


        val encryptedData = cipher.doFinal(paddedData)
        Log.d(
            "EncryptionAES",
            "Encrypted byte array length (before Base64 encoding): ${encryptedData.size} bytes"
        )


        val base64EncodedEncryptedData = Base64.getEncoder().encodeToString(encryptedData)
        return base64EncodedEncryptedData
    }
}
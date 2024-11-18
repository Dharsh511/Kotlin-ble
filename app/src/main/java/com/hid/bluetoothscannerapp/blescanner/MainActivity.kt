package com.hid.bluetoothscannerapp.blescanner

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.auth0.android.jwt.JWT
import com.hid.bluetoothscannerapp.R
import java.util.UUID
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// EncryptionAES class definition (same as you provided)
class EncryptionAES {

    // ANSI X9.23 padding function
    private fun padAnSix923(data: ByteArray, blockSize: Int): ByteArray {
        val paddingLength = blockSize - (data.size % blockSize)
        val paddingBytes = ByteArray(paddingLength)
        paddingBytes[paddingLength - 1] = paddingLength.toByte() // Add padding length as the last byte
        return data + paddingBytes
    }

    private fun removePadding(data: ByteArray): ByteArray {
        val paddingLength = data[data.size - 1].toInt() // Read padding length from the last byte
        return data.copyOfRange(0, data.size - paddingLength) // Remove padding bytes
    }

    // Encrypt the ID token using AES-ECB with the key from its custom claim
    fun encryptIDTokenWithCustomKey(idToken: String, claimName: String): String {
        // Decode the JWT to get the custom claim containing the Base64-encoded AES key
        val decodedJWT = JWT(idToken)
        val base64EncodedKey = decodedJWT.getClaim(claimName).asString()

        // Decode the Base64 key to raw bytes
        val aesKeyBytes = Base64.getDecoder().decode(base64EncodedKey)
        Log.d("keyBytes","keyasRawbytes: ${aesKeyBytes}")
        Log.d("lengthKeybytes", "length is: ${aesKeyBytes.size}")
        // Validate the AES key length
        if (aesKeyBytes.size != 16 && aesKeyBytes.size != 24 && aesKeyBytes.size != 32) {
            throw IllegalArgumentException("Invalid AES key length: ${aesKeyBytes.size}. Expected 16, 24, or 32 bytes.")
        }

        // Create AES key
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // Initialize AES cipher in ECB mode
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)

        // Pad the ID token to a multiple of 16 bytes
        val paddedData = padAnSix923(idToken.toByteArray(), 16)

        // Encrypt the padded data
        val encryptedData = cipher.doFinal(paddedData)
        Log.d("EncryptionAES", "Encrypted byte array length (before Base64 encoding): ${encryptedData.size} bytes")

        // Convert encrypted data to Base64 for easy storage or transmission
        val base64EncodedEncryptedData = Base64.getEncoder().encodeToString(encryptedData)
        return base64EncodedEncryptedData
    }

    // Decrypt the encrypted token using AES-ECB with the same key
    fun decryptIDTokenWithCustomKey(encryptedToken: String, claimName: String): String {
        // Decode the JWT to get the custom claim containing the Base64-encoded AES key
        val decodedJWT = JWT(encryptedToken)
        val base64EncodedKey = decodedJWT.getClaim(claimName).asString()

        // Decode the Base64 key to raw bytes
        val aesKeyBytes = Base64.getDecoder().decode(base64EncodedKey)

        // Validate the AES key length
        if (aesKeyBytes.size != 16 && aesKeyBytes.size != 24 && aesKeyBytes.size != 32) {
            throw IllegalArgumentException("Invalid AES key length: ${aesKeyBytes.size}. Expected 16, 24, or 32 bytes.")
        }

        // Create AES key
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // Initialize AES cipher in ECB mode for decryption
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey)

        // Decode the Base64-encoded encrypted token to byte array
        val encryptedData = Base64.getDecoder().decode(encryptedToken)

        // Decrypt the encrypted data
        val decryptedData = cipher.doFinal(encryptedData)

        // Remove padding from the decrypted data
        val decryptedToken = String(removePadding(decryptedData))

        // Log the decrypted ID token for verification
        Log.d("DecryptionAES", "Decrypted ID token: $decryptedToken")

        return decryptedToken
    }
}


@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : AppCompatActivity() {
    private lateinit var btnConnect: Button
    private lateinit var btnUnlock: Button
    private lateinit var btnDisconnect: Button
    private var bluetoothGatt: BluetoothGatt? = null
    private val deviceMacAddress = "78:21:84:A7:B5:EE"
    private val serviceUUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val unlockCharacteristicUUID = UUID.fromString("beb5483f-36e1-4688-b7f5-ea07361b26a8")

    private val PERMISSION_REQUEST_CODE = 1001
    private var currentChunkIndex = 0
    private lateinit var chunks: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val welcomeTextView: TextView = findViewById(R.id.tv_welcome)
        val sharedPreferences = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val retrievedIdToken = sharedPreferences.getString("id_token", null)

        if (retrievedIdToken != null) {
            try {
                Log.d("MainActivity", "Retrieved ID Token: $retrievedIdToken")
                // Parse and display the username from the original token
                val jwt = JWT(retrievedIdToken)
                val username = jwt.getClaim("preferred_username").asString()
                welcomeTextView.text = "Welcome, $username"

                // Encrypt the token for testing purposes
                val encryptedToken =
                    encryptIDTokenForTesting(retrievedIdToken, "key")
                Log.d("Encryption", "Encrypted Token: $encryptedToken")

                // Decrypt the token to verify integrity
                val decryptedToken =
                    decryptIDTokenWithCustomKey(encryptedToken, "key")
                Log.d("Decryption", "Decrypted Token: $decryptedToken")

                // Compare the original and decrypted tokens
                if (retrievedIdToken == decryptedToken) {
                    Log.d("TokenVerification", "Decryption successful: Tokens match.")
                } else {
                    Log.e("TokenVerification", "Decryption failed: Tokens do not match.")
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing token: ${e.message}",e)
            }

        } else {
            Log.e("MainActivity", "No ID token found in SharedPreferences")
        }

        // Initialize buttons
        btnConnect = findViewById(R.id.btn_connect)
        btnUnlock = findViewById(R.id.btn_unlock)
        btnDisconnect = findViewById(R.id.btn_disconnect)

        btnUnlock.isEnabled = false
        btnDisconnect.isEnabled = false

        // Set click listeners
        btnConnect.setOnClickListener { connectToDevice() }
        btnUnlock.setOnClickListener {
            Log.d("MainActivity", "Unlock button clicked")
            retrieveAndSendIdToken()
        }
        btnDisconnect.setOnClickListener { disconnectFromDevice() }

        // Check for Bluetooth permissions
        if (!checkBluetoothPermission()) {
            requestBluetoothPermission()
        }
    }

    private fun encryptIDTokenForTesting(idToken: String, claimName: String): String {
        val encryptionAES = EncryptionAES()
        return encryptionAES.encryptIDTokenWithCustomKey(idToken, claimName)
    }

    private fun decryptIDTokenWithCustomKey(encryptedToken: String, claimName: String): String {
        val encryptionAES = EncryptionAES()
        return encryptionAES.decryptIDTokenWithCustomKey(encryptedToken, claimName)
    }

    private fun retrieveAndSendIdToken() {
        // Retrieve ID token from SharedPreferences
        Log.d("MainActivity", "retrieveAndSendIdToken called")
        val sharedPreferences = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val retrievedIdToken = sharedPreferences.getString("id_token", null)

        if (retrievedIdToken != null) {
            Log.d("MainActivity", "Encrypting and sending ID Token: $retrievedIdToken")

            // Encrypt the ID token using AES encryption
            val encryptionAES = EncryptionAES()
            val encryptedToken = encryptionAES.encryptIDTokenWithCustomKey(retrievedIdToken, "key")

            Log.d("MainActivity", "Encrypted ID Token: $encryptedToken")
            Log.d("MainActivity", "Encrypted ID Token length: ${encryptedToken.length}")

            sendIdTokenInChunks(encryptedToken) // Send the encrypted token
        } else {
            Log.d("MainActivity", "No ID Token found")
            Toast.makeText(this, "ID Token not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendIdTokenInChunks(encryptedIdToken: String, chunkSize: Int = 512) {
        chunks = encryptedIdToken.chunked(chunkSize)
        currentChunkIndex = 0
        sendNextChunk()
    }

    private var startTime: Long = 0
    private var endTime: Long = 0

    private fun sendNextChunk() {
        if (currentChunkIndex == 0) {
            // Start time when sending the first chunk
            startTime = System.currentTimeMillis()
            Log.d("sendNextChunk", "Sending chunks started at: $startTime ms")
        }

        if (currentChunkIndex < chunks.size) {
            var nextChunk = chunks[currentChunkIndex]

            if ((currentChunkIndex == chunks.size - 1) && !nextChunk.contains("END_OF_TOKEN")) {
                nextChunk += "END_OF_TOKEN"
                Log.d("sendNextChunk", "Appending END_OF_TOKEN to the last chunk")
            }
            Log.d("sendNextChunk", "Sending next chunk: $nextChunk")
            sendCommandToM5Stack(nextChunk)
            currentChunkIndex++
        } else {
            endTime = System.currentTimeMillis()
            Log.d("sendNextChunk", "All chunks sent")

            val totalDuration = endTime - startTime
            Log.d("sendNextChunk", "Total time taken to send all chunks: $totalDuration ms")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun sendCommandToM5Stack(tokenChunk: String) {
        Log.d("sendCommandToM5Stack", "Method called with token length: ${tokenChunk.length}")

        bluetoothGatt?.let { gatt ->
            Log.d("sendCommandToM5Stack", "BluetoothGatt instance found, attempting to send token")

            // Fetch the desired service and characteristic
            val service = gatt.getService(serviceUUID)
            if (service == null) {
                Log.d("sendCommandToM5Stack", "Service with UUID $serviceUUID not found")
                Toast.makeText(this, "Service not found", Toast.LENGTH_SHORT).show()
                return
            }

            val characteristic = service.getCharacteristic(unlockCharacteristicUUID)
            if (characteristic == null) {
                Log.d("sendCommandToM5Stack", "Characteristic with UUID $unlockCharacteristicUUID not found")
                Toast.makeText(this, "Characteristic not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Check if the characteristic is writable
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                Log.d("sendCommandToM5Stack", "Characteristic is writable")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = tokenChunk.toByteArray()

                Log.d("sendCommandToM5Stack", "Encrypted Token Length: ${tokenChunk.length}")

                // Check for permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("sendCommandToM5Stack", "Bluetooth permission not granted, requesting permission")
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
                    return
                }

                // Write characteristic to device
                val success = gatt.writeCharacteristic(characteristic)
                if (success) {
                    Log.d("sendCommandToM5Stack", "Successfully sent the token chunk to M5Stack")
                } else {
                    Log.d("sendCommandToM5Stack", "Failed to send the token chunk to M5Stack")
                }
            } else {
                Log.d("sendCommandToM5Stack", "Characteristic is not writable")
            }
        } ?: run {
            Log.d("sendCommandToM5Stack", "BluetoothGatt is null")
        }
    }

    private fun connectToDevice() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceMacAddress)
        checkBluetoothPermission()
        bluetoothDevice?.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("CheckConnect", "Connected to device")
                    checkBluetoothPermission()
                    gatt.discoverServices()

                    runOnUiThread {
                        btnUnlock.isEnabled = true
                        btnDisconnect.isEnabled = true
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("MainActivity", "Disconnected from device")
                    gatt.close()

                    runOnUiThread {
                        btnUnlock.isEnabled = false
                        btnDisconnect.isEnabled = false
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                checkBluetoothPermission()
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("MainActivity", "Services discovered on device")
                    gatt.requestMtu(512)
                } else {
                    Log.e("MainActivity", "Service discovery failed")
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("MainActivity", "Chunk sent successfully")
                    sendNextChunk()
                } else {
                    Log.e("MainActivity", "Failed to send chunk")
                }
            }
        })?.let { bluetoothGatt = it }
    }




    private fun disconnectFromDevice() {
        bluetoothGatt?.let { gatt ->
            checkBluetoothPermission()
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null
            Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
            btnUnlock.isEnabled = false
            btnDisconnect.isEnabled = false
        } ?: run {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show()
        }
    }

    // Check for Bluetooth permissions
    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Request Bluetooth permissions if not already granted
    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                PERMISSION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Bluetooth permission granted")
            } else {
                Log.d("MainActivity", "Bluetooth permission denied")
                Toast.makeText(this, "Bluetooth permission is required for the app to function", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

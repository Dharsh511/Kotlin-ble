package com.hid.bluetoothscannerapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

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

    private fun retrieveAndSendIdToken() {
        // Retrieve ID token from SharedPreferences
        Log.d("MainActivity", "retrieveAndSendIdToken called")
        val sharedPreferences = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val retrievedIdToken = sharedPreferences.getString("id_token", null)

        if (retrievedIdToken != null) {
            Log.d("MainActivity", "Sending ID Token: $retrievedIdToken")
            sendIdTokenInChunks(retrievedIdToken)
        } else {
            Log.d("MainActivity", "No ID Token found")
            Toast.makeText(this, "ID Token not found", Toast.LENGTH_SHORT).show()
        }
    }

    // Connect to the specific device using its MAC address
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
                    gatt.requestMtu(256)
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

    private fun sendIdTokenInChunks(retrievedIdToken: String, chunkSize: Int =256) {
        chunks = retrievedIdToken.chunked(chunkSize)
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
            val nextChunk = chunks[currentChunkIndex]
            Log.d("sendNextChunk", "Sending next chunk: $nextChunk")
            sendCommandToM5Stack(nextChunk)
            currentChunkIndex++
        }
        else {
            endTime = System.currentTimeMillis()
            //Log.d("sendNextChunk","Sending chunks ended at: $endTime ms")
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

                Log.d("sendCommandToM5Stack", "ID Token Length: ${tokenChunk.length}")

                // Check for permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("sendCommandToM5Stack", "Bluetooth permission not granted, requesting permission")
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
                    return
                }

                // Write characteristic to device
                val success = gatt.writeCharacteristic(characteristic)
                if (success) {
                    Log.d("sendCommandToM5Stack", "Chunk sent successfully")
                } else {
                    Log.d("sendCommandToM5Stack", "Failed to send chunk")
                    Toast.makeText(this, "Failed to send ID token", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("sendCommandToM5Stack", "Characteristic is not writable")
                Toast.makeText(this, "Characteristic not writable", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.d("sendCommandToM5Stack", "BluetoothGatt is null, not connected to device")
            Toast.makeText(this, "Not connected to device", Toast.LENGTH_SHORT).show()
        }
    }

    // Disconnect from the device
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
                this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                PERMISSION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), PERMISSION_REQUEST_CODE)
        }
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}

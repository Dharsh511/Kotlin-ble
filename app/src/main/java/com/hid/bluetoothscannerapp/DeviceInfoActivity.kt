package com.hid.bluetoothscannerapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hid.bluetoothscannerapp.blescanner.model.BleDevice
import java.util.*

class DeviceInfoActivity : AppCompatActivity(){

    private var gatt: BluetoothGatt? = null
    private lateinit var deviceName: String
    private lateinit var deviceAddress: String
    private var rssi: Int = 0
    private lateinit var uuid: String
    private lateinit var bondState: String
    private var bluetoothGatt: BluetoothGatt? = null
    private val PERMISSION_REQUEST_CODE = 0

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        deviceName = intent.getStringExtra("DEVICE_NAME") ?: "Unknown"
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS") ?: "Unknown"
        rssi = intent.getIntExtra("RSSI", 0)
        val device: BleDevice? = intent.getParcelableExtra("BleDevice")

        val deviceInfoTextView = findViewById<TextView>(R.id.device_info)
        deviceInfoTextView.text = "Name: $deviceName\nAddress: $deviceAddress\nRSSI: $rssi\n"

        val unlockButton = findViewById<Button>(R.id.btn_unlock)
        val lockButton = findViewById<Button>(R.id.btn_lock)
        val disconnect = findViewById<Button>(R.id.btn_disconnect)

        unlockButton.setOnClickListener {
            if (device != null) {
                connectToDevice(device)  // Connect and send unlock command
            }
        }

        lockButton.setOnClickListener {
            sendCommandToM5Stack("lock")  // Send lock command
        }

        disconnect.setOnClickListener {
            if (device != null) {
                sendCommandToM5Stack("disconnect")  // First, send the disconnect command
                Handler().postDelayed({
                    disconnectFromDevice(device)  // Then, disconnect from the device after a delay
                }, 1000)  // 1 second delay to allow the command to be processed
            }
        }
    }

    // Connect to the BLE device and handle the connection process
    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectToDevice(device: BleDevice) {
        Toast.makeText(this, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        checkBluetoothPermission()

        bluetoothDevice?.connectGatt(this, false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                checkBluetoothPermission()
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("TAG", "Connected to ${device.name}")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("TAG", "Disconnected from ${device.name}")
                    runOnUiThread {
                        Toast.makeText(this@DeviceInfoActivity, "Device disconnected", Toast.LENGTH_SHORT).show()
                    }
                    gatt.close()
                }
            }

            @RequiresApi(Build.VERSION_CODES.S)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("TAG", "Services discovered on ${device.name}")
                    checkBluetoothPermission()
                    sendCommandToM5Stack("unlock")  // Send unlock command after services are discovered
                } else {
                    Log.e("TAG", "Service discovery failed on ${device.name}, status: $status")
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("TAG", "Command sent successfully to ${device.name}")
                    runOnUiThread {
                        Toast.makeText(this@DeviceInfoActivity, "Unlock command sent successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("TAG", "Failed to send command to ${device.name}, status: $status")
                    runOnUiThread {
                        Toast.makeText(this@DeviceInfoActivity, "Failed to send unlock command", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })?.let {
            bluetoothGatt = it
        } ?: run {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBluetoothPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
        }
    }

    // Send command to M5Stack device
    @RequiresApi(Build.VERSION_CODES.S)
    private fun sendCommandToM5Stack(command: String) {
        val serviceUUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val characteristicUUID = UUID.fromString("beb5483f-36e1-4688-b7f5-ea07361b26a8")

        bluetoothGatt?.let { gatt ->
            val service = gatt.getService(serviceUUID)
            val characteristic = service?.getCharacteristic(characteristicUUID)

            characteristic?.let {
                if (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
                    Log.d("TAG", "Characteristic not writable")
                    Toast.makeText(this, "Characteristic not writable", Toast.LENGTH_SHORT).show()
                    return
                }

                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                it.value = command.toByteArray()

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
                    return
                }

                val success = gatt.writeCharacteristic(it)
                if (success) {
                    Log.d("TAG", "Command sent successfully: $command")
                } else {
                    Log.d("TAG", "Failed to send command: $command")
                }
            } ?: run {
                Toast.makeText(this, "Characteristic not found", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Gatt not initialized", Toast.LENGTH_SHORT).show()
        }
    }

    // Disconnect from the BLE device
    @RequiresApi(Build.VERSION_CODES.S)
    private fun disconnectFromDevice(device: BleDevice) {
        bluetoothGatt?.let { gatt ->
            Log.d("TAG", "Attempting to disconnect from ${device.name}")
            checkBluetoothPermission()

            // Disconnect the GATT connection
            gatt.disconnect()

            // Handle the disconnection in the existing callback that was set during connection
        } ?: run {
            runOnUiThread {
                Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onPause() {
        checkBluetoothPermission()
        super.onPause()
        gatt?.close()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
    }
}

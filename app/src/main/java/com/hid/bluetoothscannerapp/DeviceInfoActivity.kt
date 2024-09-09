package com.hid.bluetoothscannerapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
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
import com.hid.bluetoothscannerapp.blescanner.model.BleDevice
import java.util.UUID

class DeviceInfoActivity : AppCompatActivity() {

    private  var gatt: BluetoothGatt? =null
    private lateinit var deviceName: String
    private lateinit var deviceAddress: String
    private var rssi: Int = 0
    private lateinit var Uuid : String
    private lateinit var BondState: String
    private var bluetoothGatt: BluetoothGatt? = null
    // Request permissions if not granted
    val PERMISSION_REQUEST_CODE =0
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_info)

        deviceName = intent.getStringExtra("DEVICE_NAME") ?: "Unknown"
        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS") ?: "Unknown"
        rssi = intent.getIntExtra("RSSI", 0)
        val device: BleDevice? = intent.getParcelableExtra("BleDevice")
      //  Uuid = intent.getStringExtra("UUID") ?: "-"
      //  BondState = intent.getStringExtra("BOND_STATE") ?: "-"


        val deviceInfoTextView = findViewById<TextView>(R.id.device_info)
        deviceInfoTextView.text = "Name: $deviceName\nAddress: $deviceAddress\nRSSI: $rssi\n"

        val unlockButton = findViewById<Button>(R.id.btn_unlock)
        val lockButton = findViewById<Button>(R.id.btn_lock)
        val disconnect = findViewById<Button>(R.id.btn_disconnect)


        unlockButton.setOnClickListener {
            if (device != null) {
                connectToDevice(device , false)
            }
        }

        lockButton.setOnClickListener {
            sendCommandToM5Stack("lock")
        }

        disconnect.setOnClickListener {
            if (device != null) {
               disconnectFromDevice(device)

            }
        }
    }


    private fun connectToDevice(device: BleDevice , isdisconnect :Boolean) {

        Toast.makeText(this, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)

        bluetoothDevice?.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("TAG", "Connected to ${device.name}")
                    gatt.discoverServices()
                    if (isdisconnect){
                        disconnectFromDevice(device)

                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("TAG", "Disconnected from ${device.name}")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("TAG", "Services discovered on ${device.name}")
                    if (!isdisconnect) {
                        sendCommandToM5Stack("unlock")
                    }


                } else {
                    Log.e("TAG", "Service discovery failed on ${device.name}, status: $status")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("TAG", "Characteristic read successfully from ${device.name}")
                } else {
                    Log.e("TAG", "Characteristic read failed from ${device.name}, status: $status")
                }
            }

            // You can override more callback methods here as needed
        })?.let {
            bluetoothGatt = it
        } ?: run {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
        }
    }




    @RequiresApi(Build.VERSION_CODES.S)
    private fun sendCommandToM5Stack(command: String) {
        val serviceUUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val unlockCharacteristicUUID = UUID.fromString("beb5483f-36e1-4688-b7f5-ea07361b26a8")

        bluetoothGatt?.let { gatt ->
            val service = gatt.getService(serviceUUID)
            val characteristic = service?.getCharacteristic(unlockCharacteristicUUID)

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
                    Toast.makeText(this, "Sent $command command", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to send $command", Toast.LENGTH_SHORT).show()
                }
                gatt.setCharacteristicNotification(it, true)
            } ?: run {
                Toast.makeText(this, "Characteristic not found", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Gatt not initialized", Toast.LENGTH_SHORT).show()
        }
    }



    private fun disconnectFromDevice(device: BleDevice) {
        bluetoothGatt?.let { gatt ->
            Log.d("TAG", "Disconnecting ")
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        gatt?.close()
    }



    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
    }
}



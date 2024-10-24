package com.hid.bluetoothscannerapp

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hid.bluetoothscannerapp.blescanner.BleScanManager
import com.hid.bluetoothscannerapp.blescanner.adapter.BleDeviceAdapter
import com.hid.bluetoothscannerapp.blescanner.model.BleDevice
import com.hid.bluetoothscannerapp.blescanner.model.BleScanCallback
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : AppCompatActivity() {
    private lateinit var btnStartScan: Button
    private lateinit var bleScanManager: BleScanManager
    private lateinit var foundDevices: MutableList<BleDevice>
    private lateinit var context: Context
    private var bluetoothGatt: BluetoothGatt? = null

    private val REQUEST_CODE_BLUETOOTH_PERMISSION = 1001
    private val PERMISSION_REQUEST_CODE = 0

//    Sets up the layout for the activity.
//    Initializes the foundDevices list to store discovered Bluetooth devices.
//    Configures a RecyclerView to display found devices.
//    Checks for Bluetooth permissions and initiates a scan if permissions are granted.
//    Sets up listeners for starting scans and handling device connections/disconnections.
//

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        context = this
        foundDevices = mutableListOf()
        val rvFoundDevices = findViewById<RecyclerView>(R.id.rv_found_devices)

        checkBluetoothPermission()

        val adapter = BleDeviceAdapter(
            devices = foundDevices,
            onConnectClickListener = { device ->
                connectToDevice(device)
            },
            onDisconnectClickListener = { device ->
                disconnectFromDevice(device)
            }
        )

        rvFoundDevices.adapter = adapter
        rvFoundDevices.layoutManager = LinearLayoutManager(this)

        val btManager = getSystemService(BluetoothManager::class.java)
        bleScanManager = BleScanManager(btManager, 5000, scanCallback = BleScanCallback( {
            val name = it.name
            val rssi = it.rssi
            val address = it.address

            if (name.isBlank()) return@BleScanCallback

            val bleDevice = BleDevice(name, address, rssi)
            if (!foundDevices.contains(bleDevice)) {
                Log.d(BleScanCallback::class.java.simpleName, "Found device: $name")
                foundDevices.add(bleDevice)
                adapter.notifyItemInserted(foundDevices.size - 1)
            }
        }))

        btnStartScan = findViewById(R.id.btn_start_scan)
        btnStartScan.setOnClickListener {
            if (checkBluetoothPermission()) {
                bleScanManager.scanBleDevices()
            } else {
                requestBluetoothPermission()
            }
        }

        // Optionally, start scanning on create
        if (checkBluetoothPermission()) {
            bleScanManager.scanBleDevices()
        } else {
            requestBluetoothPermission()
        }
    }

//    Checks if Bluetooth permissions are granted.
//    Attempts to establish a GATT connection with the selected device.
//    If connected, it calls discoverServices() to explore available services on the device.
//    It triggers the onServicesDiscovered() callback to handle further interactions like sending commands to the device.

    private fun connectToDevice(device: BleDevice) {
        checkBluetoothPermission()
        Toast.makeText(this, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)

        bluetoothDevice?.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to ${device.name}")
                    checkBluetoothPermission()
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from ${device.name}")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered on ${device.name}")
                  //  sendCommandToM5Stack("unlock")
                    val intent = Intent(this@MainActivity, DeviceInfoActivity::class.java).apply {
                        putExtra("DEVICE_NAME", device.name)
                        putExtra("DEVICE_ADDRESS", device.address)
                        putExtra("RSSI", device.rssi)
                        putExtra("BleDevice", device)
                    }
                    startActivity(intent)
                } else {
                    Log.e(TAG, "Service discovery failed on ${device.name}, status: $status")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Characteristic read successfully from ${device.name}")
                } else {
                    Log.e(TAG, "Characteristic read failed from ${device.name}, status: $status")
                }
            }

            // You can override more callback methods here as needed
        })?.let {
            bluetoothGatt = it
        } ?: run {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
        }
    }
//    This method is used to send a command to the connected M5Stack device via a specific GATT characteristic. It:
//
//    Looks up the GATT service and characteristic using UUIDs.
//    Checks if the characteristic is writable and sends the specified command if possible.
//    Displays success or failure messages based on the outcome
//    .
    @RequiresApi(Build.VERSION_CODES.S)
    private fun sendCommandToM5Stack(command: String) {
        val serviceUUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val unlockCharacteristicUUID = UUID.fromString("beb5483f-36e1-4688-b7f5-ea07361b26a8")

        bluetoothGatt?.let { gatt ->
            val service = gatt.getService(serviceUUID)
            val characteristic = service?.getCharacteristic(unlockCharacteristicUUID)

            characteristic?.let {
                if (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
                    Log.d(TAG, "Characteristic not writable")
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
//    This method handles the disconnection from a connected BLE device. It:
//
//    Checks if there’s an active GATT connection.
//    Disconnects and closes the GATT connection.
//    Displays a message confirming the disconnection.

    private fun disconnectFromDevice(device: BleDevice) {
        bluetoothGatt?.let { gatt ->
            Log.d(TAG, "Disconnecting from ${device.name}")
            checkBluetoothPermission()
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null
            Toast.makeText(this, "Disconnected from ${device.name}", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show()
        }
    }
//    This method clears the list of found devices and notifies the adapter to update the RecyclerView. It:
//
//    Removes all items from the foundDevices list.
//    Informs the adapter that the list has been cleared.

    private fun clearFoundDevices(adapter: BleDeviceAdapter) {
        val size = foundDevices.size
        foundDevices.clear()
        adapter.notifyItemRangeRemoved(0, size)
    }

//    This method checks if the required Bluetooth permissions are granted. It:
//
//    Verifies permissions based on the Android SDK version (pre or post Android 12).
//    Returns true if all permissions are granted, false otherwise.


    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermissions(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            hasPermissions(Manifest.permission.BLUETOOTH)
        }
    }
//    This method checks if a specific set of permissions is granted. It:
//
//    Loops through the provided permissions and checks each one.
//    Returns true if all requested permissions are granted, false otherwise.
    private fun hasPermissions(vararg permissions: String): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
//    This method requests Bluetooth-related permissions from the user if they haven’t been granted. It:
//
//    Checks if the permissions are already granted.
//    If not, requests the appropriate Bluetooth and location permissions based on the Android SDK version.
    private fun requestBluetoothPermission() {
        if (!checkBluetoothPermission()) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION , Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.BLUETOOTH)
            }
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_BLUETOOTH_PERMISSION)
        }
    }
//    This method is called when the user responds to a permission request. It:
//
//    Checks the result of the permission request.
//    If granted, it starts scanning for BLE devices.
//    If denied, it displays a message informing the user that Bluetooth permissions were denied.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bleScanManager.scanBleDevices()
            } else {
                Toast.makeText(this, getString(R.string.ble_permissions_denied_message), Toast.LENGTH_LONG).show()
            }
        }
    }
//This holds a constant TAG used for logging purposes throughout the activity.
    companion object {
        private const val TAG = "MainActivity"
    }
}

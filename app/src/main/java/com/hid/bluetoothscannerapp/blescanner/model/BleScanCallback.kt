package com.hid.bluetoothscannerapp.blescanner.model

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log

class BleScanCallback(
    private val onScanResultAction: (BleDevice) -> Unit = {},
    private val onBatchScanResultAction: (List<BleDevice>) -> Unit = {},
    private val onScanFailedAction: (Int) -> Unit = {}
) : ScanCallback() {


    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        result?.let {
            val name = it.device.name ?: "Unknown"
            val address = it.device.address
            val rssi = it.rssi
            val device = BleDevice(name, address, rssi)
            onScanResultAction(device)
        }
    }



    @SuppressLint("MissingPermission")
    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
        super.onBatchScanResults(results)
        val devices = results?.map {
            BleDevice(it.device.name ?: "Unknown", it.device.address, it.rssi)
        } ?: emptyList()
        onBatchScanResultAction(devices)
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.e(TAG, "BleScanCallback - scan failed with error '$errorCode'")
        onScanFailedAction(errorCode)
    }



    companion object {
        private val TAG = BleScanCallback::class.java.simpleName
    }


}

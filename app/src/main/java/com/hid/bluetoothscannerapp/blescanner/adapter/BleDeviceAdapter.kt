package com.hid.bluetoothscannerapp.blescanner.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hid.bluetoothscannerapp.R
import com.hid.bluetoothscannerapp.blescanner.model.BleDevice

class BleDeviceAdapter(
    private val devices: List<BleDevice>,
    private val onConnectClickListener: (BleDevice) -> Unit,
    private val onDisconnectClickListener: (BleDevice) -> Unit // Added disconnect listener
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.device_name)
        val rssiTextView: TextView = itemView.findViewById(R.id.device_rssi)
        val devicesAddressTextView: TextView = itemView.findViewById(R.id.device_address)
        val buttonConnect: Button = itemView.findViewById(R.id.btn_connect)
        val buttonDisconnect: Button = itemView.findViewById(R.id.btn_disconnect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val deviceView = inflater.inflate(R.layout.device_row_layout, parent, false)
        return ViewHolder(deviceView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceNameTextView.text = device.name
        holder.rssiTextView.text = "RSSI: ${device.rssi} dBm"
        holder.devicesAddressTextView.text = device.address

        holder.buttonConnect.setOnClickListener {
            onConnectClickListener(device)
        }

        holder.buttonDisconnect.setOnClickListener {
            onDisconnectClickListener(device)
        }
    }

    override fun getItemCount(): Int {
        return devices.size
    }
}

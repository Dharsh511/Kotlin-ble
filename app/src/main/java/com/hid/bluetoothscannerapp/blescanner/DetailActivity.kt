package com.hid.bluetoothscannerapp.blescanner

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hid.bluetoothscannerapp.R

class DetailActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets


        }

        val deviceName = intent?.getStringExtra("device_name")

        // Ensure deviceName is not null before using it
        if (deviceName != null) {
            // Find the TextView in your layout
            val textView = findViewById<TextView>(R.id.textView)
            // Set the device name as the text of the TextView
            textView.text = deviceName
        } else {
            // Handle the case where deviceName is null (optional)
            Toast.makeText(this, "Device name not found!", Toast.LENGTH_SHORT).show()
        }
    }
}



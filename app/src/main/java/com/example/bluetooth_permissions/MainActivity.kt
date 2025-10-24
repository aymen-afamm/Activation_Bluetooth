package com.example.bluetooth_permissions

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchBluetooth: Switch
    private lateinit var statusText: TextView
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothReceiver: BroadcastReceiver
    private var permissionAsked = false // only once per session

    // Runtime permission (Android 12+)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Permission Bluetooth accordée ✅", Toast.LENGTH_SHORT).show()
                updateBluetoothStatus()
            } else {
                Toast.makeText(this, "Permission Bluetooth refusée ❌", Toast.LENGTH_SHORT).show()
                switchBluetooth.isEnabled = false
                statusText.text = "Permission refusée"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchBluetooth = findViewById(R.id.switchBluetooth)
        statusText = findViewById(R.id.textStatus)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Check if Bluetooth hardware exists
        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth non disponible ❌"
            switchBluetooth.isEnabled = false
            return
        }

        updateBluetoothStatus()

        switchBluetooth.setOnCheckedChangeListener { _, isChecked ->
            // Request permission only once if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (!permissionAsked) {
                    permissionAsked = true
                    requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    Toast.makeText(this, "Permission déjà demandée ❌", Toast.LENGTH_SHORT).show()
                }
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                turnBluetoothOn()
            } else {
                turnBluetoothOff()
            }
        }

        // Receiver to automatically detect Bluetooth ON/OFF
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> updateBluetoothStatus()
                }
            }
        }
    }

    private fun updateBluetoothStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            statusText.text = "Permission requise pour le Bluetooth ⚠️"
            return
        }

        if (bluetoothAdapter?.isEnabled == true) {
            switchBluetooth.isChecked = true
            statusText.text = "Bluetooth activé ✅"
        } else {
            switchBluetooth.isChecked = false
            statusText.text = "Bluetooth désactivé ❌"
        }
    }

    private fun turnBluetoothOn() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableIntent)
            Toast.makeText(this, "Activation du Bluetooth...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun turnBluetoothOff() {
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter?.disable()
            Toast.makeText(this, "Désactivation du Bluetooth...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        updateBluetoothStatus()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(bluetoothReceiver)
    }
}

package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var switchBluetooth: Switch
    private lateinit var statusText: TextView
    private lateinit var devicesListText: TextView
    private lateinit var pairedDeviceNameEdit: EditText // EditText pour l'appareil connecté

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothStateReceiver: BroadcastReceiver
    private lateinit var bluetoothBondReceiver: BroadcastReceiver

    // Lanceur de permission ciblant ACCESS_FINE_LOCATION (Android 8)
    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Permission de Localisation accordée ✅", Toast.LENGTH_SHORT).show()
                updateBluetoothStatus()
            } else {
                Toast.makeText(this, "Permission de Localisation refusée ❌", Toast.LENGTH_SHORT).show()
                statusText.text = "Permission de Localisation refusée"
                switchBluetooth.isChecked = false
            }
        }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialisation des vues
        switchBluetooth = findViewById(R.id.switchBluetooth)
        statusText = findViewById(R.id.textStatus)
        devicesListText = findViewById(R.id.textDevicesList)
        pairedDeviceNameEdit = findViewById(R.id.editPairedDeviceName) // FIX: Connexion au XML

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            statusText.text = "Bluetooth non disponible ❌"
            switchBluetooth.isEnabled = false
            return
        }

        checkAndRequestLocationPermission()
        updateBluetoothStatus()

        // Listener du Switch
        switchBluetooth.setOnCheckedChangeListener { _, isChecked ->

            if (!hasLocationPermission()) {
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                switchBluetooth.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                turnBluetoothOn()
            } else {
                turnBluetoothOff()
            }
        }

        // 2. Receiver pour l'état ON/OFF du Bluetooth
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> updateBluetoothStatus()
                }
            }
        }

        // 3. Receiver pour l'état d'appariement et de CONNEXION
        bluetoothBondReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                // L'appareil concerné par l'événement
                val device: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val deviceName = device?.name ?: "Appareil Inconnu"

                when (action) {

                    // Gère la CONNEXION réelle
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        pairedDeviceNameEdit.setText("Connecté: $deviceName")
                        Toast.makeText(context, "$deviceName est connecté ✅", Toast.LENGTH_LONG).show()
                    }

                    // Gère la DÉCONNEXION
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        pairedDeviceNameEdit.setText("Déconnecté: $deviceName")
                        Toast.makeText(context, "$deviceName s'est déconnecté ❌", Toast.LENGTH_LONG).show()
                        // Optionnel: Vider après un moment ou mettre à jour la liste
                    }

                    // Gère l'ÉPARIEMENT (Bonding) initial
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        if (bondState == BluetoothDevice.BOND_BONDING) {
                            pairedDeviceNameEdit.setText("Appariement en cours avec: $deviceName")
                        } else if (bondState == BluetoothDevice.BOND_BONDED) {
                            // Si apparié, on attend l'événement ACTION_ACL_CONNECTED pour confirmer la connexion
                            Toast.makeText(context, "$deviceName est maintenant apparié (Bonded) ✔️", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // =======================================================
    // == Fonctions de Permission ==
    // =======================================================

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestLocationPermission() {
        if (!hasLocationPermission()) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // =======================================================
    // == Fonctions Bluetooth et d'Affichage ==
    // =======================================================

    @SuppressLint("MissingPermission")
    private fun updateBluetoothStatus() {
        if (!hasLocationPermission()) {
            statusText.text = "Permission Location requise ⚠️"
            devicesListText.text = "Activez la localisation pour voir les appareils."
            return
        }

        if (bluetoothAdapter?.isEnabled == true) {
            switchBluetooth.isChecked = true
            statusText.text = "Bluetooth activé ✅"
            displayPairedDevices()
        } else {
            switchBluetooth.isChecked = false
            statusText.text = "Bluetooth désactivé ❌"
            devicesListText.text = "Bluetooth désactivé."
            pairedDeviceNameEdit.setText("Bluetooth désactivé.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun displayPairedDevices() {
        if (!hasLocationPermission()) {
            devicesListText.text = "Localisation manquante pour afficher la liste."
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val deviceList = StringBuilder("Appareils appariés:\n")

        if (!pairedDevices.isNullOrEmpty()) {
            for (device in pairedDevices) {
                val deviceName = device.name ?: "Nom inconnu"
                deviceList.append(" --> $deviceName \n")
            }
            devicesListText.text = deviceList.toString()
        } else {
            devicesListText.text = "Aucun appareil n'est apparié actuellement."
        }
    }

    private fun turnBluetoothOn() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableIntent)
            Toast.makeText(this, "Demande d'activation du Bluetooth...", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun turnBluetoothOff() {
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter?.disable()
            Toast.makeText(this, "Désactivation du Bluetooth...", Toast.LENGTH_SHORT).show()
        }
    }

    // =======================================================
    // == Cycle de Vie (Enregistrement des Receivers) ==
    // =======================================================

    override fun onResume() {
        super.onResume()

        // 1. Enregistrement du Receiver d'état (ON/OFF)
        if (hasLocationPermission()) {
            registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        }

        // 2. Enregistrement du Receiver d'appariement et de CONNEXION
        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        bondFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)   // Événement Connexion Réelle
        bondFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED) // Événement Déconnexion Réelle
        registerReceiver(bluetoothBondReceiver, bondFilter)

        updateBluetoothStatus()
    }

    override fun onPause() {
        super.onPause()

        // Désenregistrement des deux Receivers
        unregisterReceiver(bluetoothStateReceiver)
        unregisterReceiver(bluetoothBondReceiver)
    }
}

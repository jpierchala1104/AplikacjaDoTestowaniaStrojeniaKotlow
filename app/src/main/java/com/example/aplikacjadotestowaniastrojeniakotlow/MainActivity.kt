package com.example.aplikacjadotestowaniastrojeniakotlow

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.text.ParseException
import java.util.*


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    private val TAG = "MainActivity"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothConnection = BluetoothConnectionService(this)
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private var receivedMessage: String? = null
    private var mBTDevice: BluetoothDevice? = null
    private val diameter = 20
    private val tuningInfo = "tuningInfo"

    private var counter = 50
    private var tuneDirection = 0
    private val timer = Timer()
    private val tt: TimerTask = object : TimerTask() {
        override fun run() {
            counter += tuneDirection
            tuningValue.setProgress(counter)
            if (counter === 100 || counter === 0) tuneDirection = 0
        }
    }

    private val mMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            receivedMessage = intent.getStringExtra("message")
            if (receivedMessage != null)
            {
                var sentMessage = ""
                when {
                    receivedMessage == "getDiameter" -> {
                        sentMessage = diameter.toString()
                        mBluetoothConnection.write(diameter.toString().toByteArray())
                    }
                    receivedMessage == "getTuningInfo" -> {
                        tuneDirection = 0
                        sentMessage = "$counter"
                        mBluetoothConnection.write(sentMessage.toByteArray())
                    }
                    receivedMessage!!.contains("tune") -> {
                        val mes = receivedMessage!!.drop(5)
                        try {
                            counter = mes.toInt()
                        }catch (ex: ParseException){
                            Log.e("tuning", ex.toString())
                        }
                        tuning(mes)
                    }
                    receivedMessage == "a" -> {
                        sentMessage = "up $tuningInfo"
                        tuneDirection = 1
                    }
                    receivedMessage == "m" -> {
                        sentMessage = "stop $tuningInfo"
                        tuneDirection = 0
                    }
                    receivedMessage == "x" -> {
                        sentMessage = "down $tuningInfo"
                        tuneDirection = -1
                    }
                }
                display(receivedMessage!! + " " + sentMessage)
            }
        }
    }

    private val mBroadcastReceiver4: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val mDevice =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                if (mDevice!!.bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDED.")
                    mBTDevice = mDevice
                }
                if (mDevice.bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDING.")
                }
                if (mDevice.bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "BroadcastReceiver: BOND_NONE.")
                }
            }
        }
    }

    fun display(message: String){
        val tmp = messageTXT.text
        messageTXT.text = "$tmp \n Received: $message"
    }

    fun tuning(tuningInfo: String){
        Toast.makeText(this, "Tuning timpani to - $tuningInfo", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        timer.schedule(tt,0,100);
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "this device does't support bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        requestLocationPermission()
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            mBluetoothConnection.start()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBroadcastReceiver4, filter)
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, IntentFilter("incomingMessage"))

        resetBtn.setOnClickListener { resetBT() }
    }

    private fun resetBT(){
        Toast.makeText(this, "Reseting Bluetooth connection", Toast.LENGTH_LONG).show()
        mBluetoothConnection = BluetoothConnectionService(this)
        mBluetoothConnection.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(mMessageReceiver)
            unregisterReceiver(mBroadcastReceiver4)
        }catch (ex: Exception){}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (bluetoothAdapter!!.isEnabled) {
                    //Toast.makeText(this, "Bluetooth włączony", Toast.LENGTH_SHORT).show()
                    mBluetoothConnection.start()
                } else {
                    Toast.makeText(this, "Bluetooth wyłączony", Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Włączenie bluetooth anulowane", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        //Toast.makeText(this, "Informacje o lokalizaci włączone", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)){
            AppSettingsDialog.Builder(this).build().show()
        }else{
            requestLocationPermission()
        }
    }

    private fun requestLocationPermission(){
        if (!EasyPermissions.hasPermissions(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                )) {
            EasyPermissions.requestPermissions(
                    this,
                    "Do znalezienia urządzeń bluetooth potrzebne nam dane lokalizacji",
                    0,
                    Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
}
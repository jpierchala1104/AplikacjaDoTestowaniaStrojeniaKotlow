package com.example.aplikacjadotestowaniastrojeniakotlow

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*


class BluetoothConnectionService(context: Context) {
    companion object{
        private const val TAG = "BluetoothConnectionServ"
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val appName = "MYTESTAPP"
        private val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        private var mInsecureAcceptThread: AcceptThread? = null

        private var mConnectThread: ConnectThread? = null
        private var mmDevice: BluetoothDevice? = null
        private var deviceUUID: UUID? = null
        var mProgressDialog: ProgressDialog? = null
        var isConnected = false

        private lateinit var mContext: Context

        private var mConnectedThread: ConnectedThread? = null

        private fun connected(mmSocket: BluetoothSocket, mmDevice: BluetoothDevice) {
            Log.d(TAG, "connected: Starting.")

            mConnectedThread = ConnectedThread(mmSocket)
            mConnectedThread!!.start()
        }
    }

    init {
        mContext= context
    }

    private class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket?
        override fun run() {
            Log.d(TAG, "run: AcceptThread Running.")
            var socket: BluetoothSocket? = null
            try {
                Log.d(TAG, "run: RFCOM server socket start.....")
                socket = mmServerSocket!!.accept()
                Log.d(TAG, "run: RFCOM server socket accepted connection.")
            } catch (e: IOException) {
                Log.e(TAG, "AcceptThread: IOException: " + e.message)
            }

            if (socket != null) {
                mmDevice = socket.remoteDevice
                connected(socket, mmDevice!!)
            }
            Log.i(TAG, "END mAcceptThread ")
            cancel()
            mInsecureAcceptThread = null
        }

        fun cancel() {
            Log.d(TAG, "cancel: Canceling AcceptThread.")
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: Close of AcceptThread ServerSocket failed. " + e.message)
            }
        }

        init {
            var tmp: BluetoothServerSocket? = null

            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    appName,
                    MY_UUID
                )
                Log.d(TAG, "AcceptThread: Setting up Server using: $MY_UUID")
            } catch (e: IOException) {
                Log.e(TAG, "AcceptThread: IOException: " + e.message)
            }
            mmServerSocket = tmp
        }
    }

    @Synchronized fun start() {
        Log.d(TAG, "start")

        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread()
            mInsecureAcceptThread!!.start()
        }
    }

    private class ConnectThread(device: BluetoothDevice, uuid: UUID) : Thread() {
        private var mmSocket: BluetoothSocket? = null
        override fun run() {
            var tmp: BluetoothSocket? = null
            Log.i(TAG, "RUN mConnectThread ")

            try {
                Log.d(
                    TAG, "ConnectThread: Trying to create InsecureRfcommSocket using UUID: "
                            + MY_UUID
                )
                tmp = mmDevice!!.createRfcommSocketToServiceRecord(deviceUUID)
            } catch (e: IOException) {
                Log.e(TAG, "ConnectThread: Could not create InsecureRfcommSocket " + e.message)
            }
            mmSocket = tmp

            mBluetoothAdapter.cancelDiscovery()

            try {
                mmSocket!!.connect()
                isConnected = true
                Log.d(TAG, "run: ConnectThread connected.")
            } catch (e: IOException) {
                try {
                    mmSocket!!.close()
                    Log.d(TAG, "run: Closed Socket.")
                } catch (e1: IOException) {
                    Log.e(
                        TAG,
                        "mConnectThread: run: Unable to close connection in socket " + e1.message
                    )
                }
                Log.d(TAG, "run: ConnectThread: Could not connect to UUID: $MY_UUID")
                isConnected = false
            }

            connected(mmSocket!!, mmDevice!!)
            Log.i(TAG, "END mConnectThread ")
        }

        fun cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.")
                mmSocket!!.close()
                isConnected = false
            } catch (e: IOException) {
                Log.e(TAG, "cancel: close() of mmSocket in Connectthread failed. " + e.message)
            }
        }

        init {
            Log.d(TAG, "ConnectThread: started.")
            mmDevice = device
            deviceUUID = uuid
        }
    }

    private class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            Log.d(TAG, "ConnectedThread: Listening.")
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = mmInStream!!.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)
                    Log.d(TAG, "InputStream: $incomingMessage")

                    val incomingMessageIntent = Intent("incomingMessage")
                    incomingMessageIntent.putExtra("message", incomingMessage)
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(incomingMessageIntent)
                } catch (e: IOException) {
                    Log.e(TAG, "read: Error reading Input Stream. " + e.message)
                    break
                }
            }
            Log.i(TAG, "END mConnectedThread ")
            cancel()
            if (mInsecureAcceptThread == null) {
                mInsecureAcceptThread = AcceptThread()
                mInsecureAcceptThread!!.start()
            }
        }

        fun write(bytes: ByteArray?) {
            val text = String(bytes!!, Charset.defaultCharset())
            Log.d(TAG, "write: Writing to outputstream: $text")
            try {
                mmOutStream?.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "write: Error writing to output stream. " + e.message)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
                isConnected = false
            } catch (e: IOException) {
            }
        }

        init {
            Log.d(TAG, "ConnectedThread: Starting.")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                mProgressDialog?.dismiss()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    fun write(out: ByteArray?) {
        var r: ConnectedThread

        Log.d(TAG, "write: Write Called.")
        mConnectedThread?.write(out)
    }
}
package com.example.compyung

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothClient(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // HC-05 등 시리얼 통신용 표준 UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 기존 연결 종료
                disconnect()

                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                outputStream = socket?.outputStream
                Log.d("BluetoothClient", "Connected to ${device.name}")
                true
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Connection failed", e)
                try {
                    socket?.close()
                } catch (closeException: IOException) {}
                socket = null
                false
            }
        }
    }

    suspend fun sendData(message: String) {
        withContext(Dispatchers.IO) {
            try {
                outputStream?.write(message.toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Send failed", e)
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Disconnect failed", e)
        }
        socket = null
        outputStream = null
    }
    
    fun isConnected(): Boolean {
        return socket?.isConnected == true
    }
}


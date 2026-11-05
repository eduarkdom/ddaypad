package com.dday.pad

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class Hid(private val ctx: Context, private val onState: (String) -> Unit) {
    private var dev: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var lastKeys = ByteArray(8)
    private var lastBtn = 0
    private var codeBtn = 0
    private var tpBtn = 0

    private val desc = intArrayOf(
        0x05,0x01,0x09,0x06,0xA1,0x01,0x85,0x01,0x05,0x07,0x19,0xE0,0x29,0xE7,0x15,0x00,0x25,0x01,
        0x75,0x01,0x95,0x08,0x81,0x02,0x95,0x01,0x75,0x08,0x81,0x01,0x95,0x06,0x75,0x08,0x15,0x00,
        0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,0x81,0x00,0xC0,
        0x05,0x01,0x09,0x02,0xA1,0x01,0x85,0x02,0x09,0x01,0xA1,0x00,0x05,0x09,0x19,0x01,0x29,0x03,
        0x15,0x00,0x25,0x01,0x95,0x03,0x75,0x01,0x81,0x02,0x95,0x01,0x75,0x05,0x81,0x03,0x05,0x01,
        0x09,0x30,0x09,0x31,0x09,0x38,0x15,0x81,0x25,0x7F,0x75,0x08,0x95,0x03,0x81,0x06,0xC0,0xC0
    ).map { it.toByte() }.toByteArray()

    fun bondedDevices(): List<BluetoothDevice> =
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()

    fun start() {
        val ad = BluetoothAdapter.getDefaultAdapter() ?: return onState("Sin Bluetooth")
        ad.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                val d = proxy as BluetoothHidDevice
                dev = d
                val sdp = BluetoothHidDeviceAppSdpSettings("DDay Pad", "Teclado y mouse", "DDay",
                    BluetoothHidDevice.SUBCLASS1_COMBO, desc)
                d.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(),
                    object : BluetoothHidDevice.Callback() {
                        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                            if (state == BluetoothProfile.STATE_CONNECTED) {
                                host = device; onState("Conectado: ${device.name}")
                            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                host = null; onState("Desconectado")
                            }
                        }
                    })
                onState("Listo. Elige el celu del juego")
            }
            override fun onServiceDisconnected(p: Int) { dev = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun connect(d: BluetoothDevice) { dev?.connect(d); onState("Conectando...") }

    /** codes: <0x100 = tecla HID, 0x1xx = modificador, 0x2xx = botón mouse */
    fun setState(codes: Set<Int>) {
        var mods = 0; var btn = 0
        val keys = ArrayList<Int>()
        for (c in codes) when {
            c >= 0x200 -> btn = btn or (1 shl (c - 0x201))
            c >= 0x100 -> mods = mods or (c and 0xFF)
            else -> keys.add(c)
        }
        val k = ByteArray(8); k[0] = mods.toByte()
        keys.take(6).forEachIndexed { i, v -> k[2 + i] = v.toByte() }
        val h = host ?: return
        if (!k.contentEquals(lastKeys)) { lastKeys = k; dev?.sendReport(h, 1, k) }
        if (btn != codeBtn) { codeBtn = btn; sendBtn() }
    }

    fun tpButton(m: Int) { tpBtn = m; sendBtn() }

    private fun sendBtn() {
        val h = host ?: return
        lastBtn = codeBtn or tpBtn
        dev?.sendReport(h, 2, byteArrayOf(lastBtn.toByte(), 0, 0, 0))
    }

    fun move(dx: Float, dy: Float, wheel: Int = 0) {
        val h = host ?: return
        var x = dx.toInt(); var y = dy.toInt(); var w = wheel
        while (x != 0 || y != 0 || w != 0) {
            val sx = x.coerceIn(-127, 127); val sy = y.coerceIn(-127, 127); val sw = w.coerceIn(-127, 127)
            dev?.sendReport(h, 2, byteArrayOf(lastBtn.toByte(), sx.toByte(), sy.toByte(), sw.toByte()))
            x -= sx; y -= sy; w -= sw
        }
    }
}

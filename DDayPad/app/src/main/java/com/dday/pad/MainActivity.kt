package com.dday.pad

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*

@SuppressLint("MissingPermission")
class MainActivity : Activity() {
    private lateinit var hid: Hid
    private lateinit var pad: PadView
    private lateinit var kb: KeyboardView
    private lateinit var tp: TouchpadView
    private lateinit var status: TextView
    private lateinit var editRow: LinearLayout
    private lateinit var editBtn: Button

    private fun lp(w: Float) = LinearLayout.LayoutParams(-1, 0, w)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        hid = Hid(this) { m -> runOnUiThread { status.text = m } }
        pad = PadView(this, hid); kb = KeyboardView(this, hid); tp = TouchpadView(this, hid)
        status = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 12f; text = "Iniciando..." }

        fun b(t: String, f: () -> Unit) = Button(this).apply {
            text = t; textSize = 11f; minHeight = 0; minimumHeight = 0; minWidth = 0
            setPadding(20, 6, 20, 6); setOnClickListener { f() }
        }
        editRow = LinearLayout(this).apply { visibility = View.GONE }
        editRow.addView(b("+") { pad.sel?.let { it.s = (it.s * 1.12f).coerceAtMost(.5f); pad.save(); pad.invalidate() } })
        editRow.addView(b("−") { pad.sel?.let { it.s = (it.s / 1.12f).coerceAtLeast(.05f); pad.save(); pad.invalidate() } })
        editRow.addView(b("Tecla") { pad.sel?.let { pick(it) } })
        editRow.addView(b("Nuevo") { val n = Btn("E", "E", .5f, .5f, .12f); pad.btns.add(n); pad.sel = n; pick(n) })
        editRow.addView(b("Borrar") { pad.sel?.let { pad.btns.remove(it); pad.sel = null; pad.save(); pad.invalidate() } })
        editRow.addView(b("Reset") { pad.defaults(); pad.save(); pad.invalidate() })
        editRow.addView(b("Sens") {
            pad.sens = if (pad.sens >= 3f) 0.8f else pad.sens + 0.4f
            tp.sens = pad.sens + 0.2f
            Toast.makeText(this, "Sensibilidad %.1f".format(pad.sens), Toast.LENGTH_SHORT).show() })

        editBtn = b("Editar") { pad.edit = !pad.edit; pad.sel = null
            editRow.visibility = if (pad.edit) View.VISIBLE else View.GONE; pad.invalidate() }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(b("Modo") { chooseMode() })
        bar.addView(b("Bluetooth") { chooseHost() })
        bar.addView(editBtn)
        bar.addView(status)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        body.addView(bar, LinearLayout.LayoutParams(-1, -2))
        body.addView(editRow, LinearLayout.LayoutParams(-1, -2))
        body.addView(pad, lp(1f)); body.addView(tp, lp(1f)); body.addView(kb, lp(2f))
        setContentView(body)
        setMode(0)
        hid.start()
    }

    private fun chooseMode() {
        AlertDialog.Builder(this).setTitle("Modo")
            .setItems(arrayOf("Juego (botones editables)", "Teclado PC", "Touchpad", "Teclado + Touchpad")) { _, i -> setMode(i) }.show()
    }

    private fun setMode(m: Int) {
        hid.setState(emptySet()); hid.tpButton(0)
        pad.edit = false; pad.sel = null; editRow.visibility = View.GONE
        pad.visibility = if (m == 0) View.VISIBLE else View.GONE
        editBtn.visibility = if (m == 0) View.VISIBLE else View.GONE
        kb.visibility = if (m == 1 || m == 3) View.VISIBLE else View.GONE
        tp.visibility = if (m == 2 || m == 3) View.VISIBLE else View.GONE
        pad.invalidate()
    }

    private fun chooseHost() {
        val l = hid.bondedDevices()
        if (l.isEmpty()) { Toast.makeText(this, "Primero empareja este celu con el del juego en Ajustes > Bluetooth", Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle("Celu del juego")
            .setItems(l.map { it.name ?: it.address }.toTypedArray()) { _, i -> hid.connect(l[i]) }.show()
    }

    private fun pick(b: Btn) {
        AlertDialog.Builder(this).setTitle("Tecla")
            .setItems(KEY_NAMES.toTypedArray()) { _, i ->
                b.k = KEY_NAMES[i]; b.l = KEY_NAMES[i].replace("MOUSE1", "FUEGO").replace("MOUSE2", "APUNTA")
                pad.save(); pad.invalidate() }.show()
    }

    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (f) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

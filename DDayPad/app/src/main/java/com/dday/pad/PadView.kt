package com.dday.pad

import android.content.Context
import android.graphics.*
import android.view.*
import org.json.*
import kotlin.math.min

class Btn(var l: String, var k: String, var x: Float, var y: Float, var s: Float)

val SYMS = mapOf("-" to 45, "=" to 46, "[" to 47, "]" to 48, "\\" to 49, ";" to 51, "'" to 52, "`" to 53, "," to 54, "." to 55, "/" to 56)
val KEY_NAMES: List<String> = ('A'..'Z').map { "$it" } + ('0'..'9').map { "$it" } +
    listOf("SPACE","ENTER","TAB","ESC","SHIFT","CTRL","ALT","UP","DOWN","LEFT","RIGHT","MOUSE1","MOUSE2","BACKSPACE","CAPS","DEL","HOME","END","PGUP","PGDN","INS","WIN") + SYMS.keys +
    (1..12).map { "F$it" }

fun keyCode(n: String): Int = when {
    n.length == 1 && n[0] in 'A'..'Z' -> 4 + (n[0] - 'A')
    n.length == 1 && n[0] in '1'..'9' -> 30 + (n[0] - '1')
    n == "0" -> 39
    n == "SPACE" -> 44; n == "ENTER" -> 40; n == "TAB" -> 43; n == "ESC" -> 41
    n == "SHIFT" -> 0x102; n == "CTRL" -> 0x101; n == "ALT" -> 0x104
    n == "UP" -> 82; n == "DOWN" -> 81; n == "LEFT" -> 80; n == "RIGHT" -> 79
    n == "MOUSE1" -> 0x201; n == "MOUSE2" -> 0x202
    SYMS.containsKey(n) -> SYMS[n]!!
    n == "BACKSPACE" -> 42; n == "CAPS" -> 57; n == "DEL" -> 76; n == "HOME" -> 74; n == "END" -> 77
    n == "PGUP" -> 75; n == "PGDN" -> 78; n == "INS" -> 73; n == "WIN" -> 0x108
    n.startsWith("F") -> 57 + n.substring(1).toInt()
    else -> 0
}

class PadView(c: Context, private val hid: Hid) : View(c) {
    var btns = mutableListOf<Btn>()
    var edit = false
    var sel: Btn? = null
    var sens = 1.6f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val look = HashMap<Int, PointF>()
    private var pressed = HashSet<Int>()
    private val prefs = c.getSharedPreferences("pad", Context.MODE_PRIVATE)

    init { load() }

    fun defaults() {
        btns = mutableListOf(
            Btn("W","W",.14f,.50f,.16f), Btn("A","A",.07f,.68f,.16f),
            Btn("S","S",.14f,.86f,.16f), Btn("D","D",.21f,.68f,.16f),
            Btn("FUEGO","MOUSE1",.90f,.62f,.22f), Btn("SALTO","SPACE",.78f,.80f,.16f),
            Btn("AGACH","CTRL",.90f,.88f,.14f), Btn("R","R",.78f,.45f,.12f),
            Btn("G","G",.90f,.32f,.12f), Btn("1","1",.35f,.08f,.10f),
            Btn("3","3",.42f,.08f,.10f), Btn("5","5",.49f,.08f,.10f),
            Btn("7","7",.56f,.08f,.10f), Btn("Z","Z",.63f,.08f,.10f),
            Btn("Q","Q",.70f,.08f,.10f), Btn("TAB","TAB",.80f,.08f,.10f),
            Btn("ENTER","ENTER",.88f,.08f,.10f))
    }

    fun save() {
        val a = JSONArray()
        btns.forEach { a.put(JSONObject().put("l", it.l).put("k", it.k).put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("s", it.s.toDouble())) }
        prefs.edit().putString("btns", a.toString()).apply()
    }

    private fun load() {
        val s = prefs.getString("btns", null)
        if (s == null) { defaults(); return }
        val a = JSONArray(s)
        btns = MutableList(a.length()) { val o = a.getJSONObject(it)
            Btn(o.getString("l"), o.getString("k"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("s").toFloat()) }
    }

    private fun rad(b: Btn) = b.s * min(width, height) / 2
    private fun hit(x: Float, y: Float) = btns.lastOrNull {
        val dx = x - it.x * width; val dy = y - it.y * height
        dx * dx + dy * dy <= rad(it) * rad(it) * 1.2f
    }

    override fun onDraw(cv: Canvas) {
        cv.drawColor(Color.rgb(18, 18, 22))
        p.textAlign = Paint.Align.CENTER
        for (b in btns) {
            val cx = b.x * width; val cy = b.y * height; val r = rad(b)
            val on = keyCode(b.k) in pressed
            p.style = Paint.Style.FILL; p.color = if (on) Color.argb(200, 255, 120, 0) else Color.argb(110, 200, 200, 210)
            cv.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = if (b === sel) 8f else 3f
            p.color = if (b === sel) Color.YELLOW else Color.WHITE
            cv.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = r * 0.55f
            cv.drawText(b.l, cx, cy + p.textSize / 3, p)
        }
        if (!edit) { p.textSize = 30f; p.color = Color.GRAY
            cv.drawText("Arrastra en zona vacía = mover cámara", width / 2f, height - 20f, p) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (edit) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> sel = hit(e.x, e.y)
                MotionEvent.ACTION_MOVE -> sel?.let { it.x = (e.x / width).coerceIn(0f, 1f); it.y = (e.y / height).coerceIn(0f, 1f) }
                MotionEvent.ACTION_UP -> save()
            }
            invalidate(); return true
        }
        val a = e.actionMasked
        val ai = e.actionIndex
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            if (hit(e.getX(ai), e.getY(ai)) == null) look[e.getPointerId(ai)] = PointF(e.getX(ai), e.getY(ai))
        }
        if (a == MotionEvent.ACTION_MOVE) for (i in 0 until e.pointerCount) {
            val lp = look[e.getPointerId(i)] ?: continue
            hid.move((e.getX(i) - lp.x) * sens, (e.getY(i) - lp.y) * sens)
            lp.set(e.getX(i), e.getY(i))
        }
        val lifting = if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) ai else -1
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) look.remove(e.getPointerId(ai))
        val now = HashSet<Int>()
        if (a != MotionEvent.ACTION_CANCEL) for (i in 0 until e.pointerCount) {
            if (i == lifting || look.containsKey(e.getPointerId(i))) continue
            hit(e.getX(i), e.getY(i))?.let { now.add(keyCode(it.k)) }
        } else look.clear()
        if (now != pressed) { pressed = now; hid.setState(now); invalidate() }
        return true
    }
}

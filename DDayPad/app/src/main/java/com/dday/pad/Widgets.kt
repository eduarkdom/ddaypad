package com.dday.pad

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.abs

private val ROWS = listOf(
    "ESC F1 F2 F3 F4 F5 F6 F7 F8 F9 F10 F11 F12 DEL",
    "` 1 2 3 4 5 6 7 8 9 0 - = BACKSPACE",
    "TAB Q W E R T Y U I O P [ ] \\",
    "CAPS A S D F G H J K L ; ' ENTER",
    "SHIFT Z X C V B N M , . / UP",
    "CTRL WIN ALT SPACE LEFT DOWN RIGHT")
private val MODS = setOf("SHIFT", "CTRL", "ALT", "WIN")

private fun wOf(n: String) = when (n) {
    "BACKSPACE" -> 2f; "TAB" -> 1.5f; "CAPS" -> 1.8f; "ENTER" -> 2.2f
    "SHIFT" -> 2.4f; "SPACE" -> 6f; "\\" -> 1.5f; else -> 1f
}

private fun label(n: String) = when (n) {
    "BACKSPACE" -> "Bksp"; "UP" -> "↑"; "DOWN" -> "↓"; "LEFT" -> "←"; "RIGHT" -> "→"; "SPACE" -> ""; else -> n
}

/** Teclado PC completo. Modificadores: un toque los deja activos hasta la siguiente tecla. */
class KeyboardView(c: Context, private val hid: Hid) : View(c) {
    private val rects = ArrayList<Pair<String, RectF>>()
    private val latched = HashSet<String>()
    private var pressed = HashSet<Int>()
    private var used = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        rects.clear()
        val rh = h / ROWS.size.toFloat()
        ROWS.forEachIndexed { r, row ->
            val names = row.split(" ")
            val total = names.sumOf { wOf(it).toDouble() }.toFloat()
            var x = 0f
            for (n in names) {
                val kw = wOf(n) / total * w
                rects.add(n to RectF(x + 3, r * rh + 3, x + kw - 3, (r + 1) * rh - 3))
                x += kw
            }
        }
    }

    private fun keyAt(x: Float, y: Float) = rects.firstOrNull { it.second.contains(x, y) }?.first

    override fun onDraw(cv: Canvas) {
        cv.drawColor(Color.rgb(24, 24, 28))
        p.textAlign = Paint.Align.CENTER
        for ((n, r) in rects) {
            p.style = Paint.Style.FILL
            p.color = if (n in latched) Color.rgb(255, 170, 0)
            else if (keyCode(n) in pressed) Color.rgb(255, 120, 0) else Color.rgb(60, 60, 70)
            cv.drawRoundRect(r, 10f, 10f, p)
            p.color = Color.WHITE; p.textSize = r.height() * 0.38f
            cv.drawText(label(n), r.centerX(), r.centerY() + p.textSize / 3, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked; val ai = e.actionIndex
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            keyAt(e.getX(ai), e.getY(ai))?.let { if (it in MODS && !latched.remove(it)) latched.add(it) }
        }
        val lifting = if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) ai else -1
        val now = HashSet<Int>()
        if (a != MotionEvent.ACTION_CANCEL) for (i in 0 until e.pointerCount) {
            if (i == lifting) continue
            keyAt(e.getX(i), e.getY(i))?.let { now.add(keyCode(it)) }
        }
        if (now.any { it < 0x100 }) used = true
        else if (used) { latched.clear(); used = false }
        val send = HashSet(now)
        latched.forEach { send.add(keyCode(it)) }
        if (send != pressed) { pressed = send; hid.setState(send); invalidate() }
        return true
    }
}

/** Touchpad: 1 dedo mueve, toque = clic, 2 dedos = scroll, toque con 2 dedos = clic derecho, franja inferior = botones. */
class TouchpadView(c: Context, private val hid: Hid) : View(c) {
    var sens = 1.8f
    private val last = HashMap<Int, PointF>()
    private val zone = HashSet<Int>()
    private var downT = 0L; private var moved = 0f; private var maxPtr = 0
    private var padDown = false; private var btnMask = 0
    private var scrollAcc = 0f; private var rx = 0f; private var ry = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        cv.drawColor(Color.rgb(24, 24, 28))
        val by = height * 0.78f
        p.style = Paint.Style.FILL; p.color = Color.rgb(45, 45, 55)
        cv.drawRoundRect(4f, 4f, width - 4f, by - 4f, 24f, 24f, p)
        p.color = if ((btnMask and 1) != 0) Color.rgb(255, 120, 0) else Color.rgb(70, 70, 80)
        cv.drawRoundRect(4f, by + 4f, width / 2f - 4f, height - 4f, 24f, 24f, p)
        p.color = if ((btnMask and 2) != 0) Color.rgb(255, 120, 0) else Color.rgb(70, 70, 80)
        cv.drawRoundRect(width / 2f + 4f, by + 4f, width - 4f, height - 4f, 24f, 24f, p)
        p.color = Color.LTGRAY; p.textAlign = Paint.Align.CENTER; p.textSize = 26f
        cv.drawText("Touchpad  ·  toque = clic  ·  2 dedos = scroll", width / 2f, by / 2, p)
        cv.drawText("Clic izq", width / 4f, (by + height) / 2, p)
        cv.drawText("Clic der", width * 3 / 4f, (by + height) / 2, p)
    }

    private fun padIds(e: MotionEvent, skip: Int): List<Int> =
        (0 until e.pointerCount).filter { it != skip && e.getPointerId(it) !in zone }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked; val ai = e.actionIndex
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val id = e.getPointerId(ai)
            last[id] = PointF(e.getX(ai), e.getY(ai))
            if (e.getY(ai) > height * 0.78f) zone.add(id) else {
                if (a == MotionEvent.ACTION_DOWN) { downT = e.eventTime; moved = 0f; padDown = true; maxPtr = 0; scrollAcc = 0f }
                maxPtr = maxOf(maxPtr, padIds(e, -1).size)
            }
        }
        if (a == MotionEvent.ACTION_MOVE) {
            val ids = padIds(e, -1)
            var sdx = 0f; var sdy = 0f
            for (i in ids) {
                val lp = last[e.getPointerId(i)] ?: continue
                sdx += e.getX(i) - lp.x; sdy += e.getY(i) - lp.y
                lp.set(e.getX(i), e.getY(i))
            }
            if (ids.size == 1) {
                rx += sdx * sens; ry += sdy * sens
                val ix = rx.toInt(); val iy = ry.toInt(); rx -= ix; ry -= iy
                if (ix != 0 || iy != 0) hid.move(ix.toFloat(), iy.toFloat())
                moved += abs(sdx) + abs(sdy)
            } else if (ids.size >= 2) {
                scrollAcc += sdy / ids.size
                val st = (scrollAcc / 25f).toInt()
                if (st != 0) { hid.move(0f, 0f, -st); scrollAcc -= st * 25f }
                moved += abs(sdy / ids.size)
            }
        }
        val lifting = if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) ai else -1
        var mask = 0
        if (a != MotionEvent.ACTION_CANCEL) for (i in 0 until e.pointerCount) {
            if (i == lifting) continue
            if (e.getPointerId(i) in zone) mask = mask or (if (e.getX(i) < width / 2f) 1 else 2)
        }
        if (mask != btnMask) { btnMask = mask; hid.tpButton(mask); invalidate() }
        if (a == MotionEvent.ACTION_UP) {
            if (padDown && e.eventTime - downT < 250 && moved < 25f) {
                hid.tpButton(if (maxPtr >= 2) 2 else 1); hid.tpButton(0)
            }
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { padDown = false; last.clear(); zone.clear() }
        else if (a == MotionEvent.ACTION_POINTER_UP) { val id = e.getPointerId(ai); last.remove(id); zone.remove(id) }
        return true
    }
}

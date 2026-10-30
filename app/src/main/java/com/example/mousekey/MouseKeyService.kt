package com.example.mousekey

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class MouseKeyService : AccessibilityService() {
    companion object { var instance: MouseKeyService? = null }

    private lateinit var wm: WindowManager
    private var cursor: View? = null
    private var cursorLp: WindowManager.LayoutParams? = null
    private var picker: View? = null
    private var pendingKey = 0
    private var suspended = false
    private var cx = 0f
    private var cy = 0f
    private var size = 0

    private fun overlay(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val info = serviceInfo
        info.motionEventSources = InputDevice.SOURCE_MOUSE
        serviceInfo = info
        size = (14 * resources.displayMetrics.density).toInt()
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC000000.toInt())
                setStroke(3, Color.WHITE)
            }
            visibility = View.INVISIBLE
        }
        cursorLp = overlay(size, size, false)
        wm.addView(dot, cursorLp)
        cursor = dot
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun moveCursor() {
        val v = cursor ?: return
        val l = cursorLp ?: return
        l.x = (cx - size / 2).toInt()
        l.y = (cy - size / 2).toInt()
        v.visibility = if (suspended) View.INVISIBLE else View.VISIBLE
        wm.updateViewLayout(v, l)
    }

    // Mouse: o botão do meio (scroll) liga/desliga o ponteiro
    override fun onMotionEvent(e: MotionEvent) {
        val press = e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
        if (press && e.actionButton == MotionEvent.BUTTON_TERTIARY) {
            suspended = !suspended
            moveCursor()
            toast(if (suspended) "Ponteiro suspenso" else "Ponteiro ativo")
            return
        }
        if (suspended) return
        cx = e.rawX
        cy = e.rawY
        moveCursor()
        when {
            press && e.actionButton == MotionEvent.BUTTON_PRIMARY ->
                if (picker != null) finishPick(cx, cy) else tap(cx, cy)
            picker != null -> {}
            press && e.actionButton == MotionEvent.BUTTON_SECONDARY -> performGlobalAction(GLOBAL_ACTION_BACK)
            e.actionMasked == MotionEvent.ACTION_SCROLL -> scroll(e.getAxisValue(MotionEvent.AXIS_VSCROLL))
        }
    }

    // Teclado: só as teclas que você mapeou são interceptadas
    override fun onKeyEvent(e: KeyEvent): Boolean {
        val m = Store.find(this, e.keyCode) ?: return false
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            if (m.type == "tap") tap(m.x, m.y) else typeText(m.text)
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 50)).build(),
            null, null
        )
    }

    private fun scroll(v: Float) {
        if (v == 0f) return
        val s = if (v > 0) 1 else -1
        val p = Path().apply {
            moveTo(cx, (cy - 150 * s).coerceAtLeast(1f))
            lineTo(cx, (cy + 150 * s).coerceAtLeast(1f))
        }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 200)).build(),
            null, null
        )
    }

    private fun typeText(s: String) {
        val n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val b = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, (n.text?.toString() ?: "") + s)
        }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    // Chamado pelo app: espera 8 s (para você abrir a tela alvo) e mostra a tela de marcar
    fun startPick(key: Int) {
        pendingKey = key
        Handler(Looper.getMainLooper()).postDelayed({ showPicker() }, 8000)
    }

    private fun showPicker() {
        val v = FrameLayout(this).apply {
            setBackgroundColor(0x88000000.toInt())
            addView(TextView(this@MouseKeyService).apply {
                text = "Toque (ou clique) onde a tecla deve clicar"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            })
            setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) finishPick(ev.rawX, ev.rawY)
                true
            }
        }
        wm.addView(v, overlay(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true))
        picker = v
    }

    private fun finishPick(x: Float, y: Float) {
        picker?.let { wm.removeView(it) }
        picker = null
        Store.add(this, Mapping(pendingKey, "tap", x, y, ""))
        toast("Posição salva")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        cursor?.let { wm.removeView(it) }
        picker?.let { wm.removeView(it) }
        cursor = null
        picker = null
        return super.onUnbind(intent)
    }
}

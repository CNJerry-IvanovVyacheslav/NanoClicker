package com.example.nanoclicker

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.saveProgress()
    }
}

class GameView(context: Context) : View(context) {

    private val prefs = context.getSharedPreferences("NanoSave", Context.MODE_PRIVATE)

    private var score = prefs.getLong("score", 0L)
    private var clickPower = prefs.getLong("clickPower", 1L)
    private var autoClick = prefs.getLong("autoClick", 0L)

    private val baseUpgradeCost = 10L
    private val baseAutoCost = 50L

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#BB86FC") }
    private val upgradePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#03DAC5") }

    private var cx = 0f
    private var cy = 0f
    private var mainRadius = 200f
    private var upg1Rect = android.graphics.RectF()
    private var upg2Rect = android.graphics.RectF()

    private val autoClicker = object : Runnable {
        override fun run() {
            if (autoClick > 0) {
                score += autoClick
                invalidate()
            }
            handler?.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler?.post(autoClicker)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler?.removeCallbacks(autoClicker)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2.5f

        val btnHeight = 150f
        val margin = 40f
        upg1Rect.set(margin, h - btnHeight * 2 - margin * 2, w - margin, h - btnHeight - margin * 2)
        upg2Rect.set(margin, h - btnHeight - margin, w - margin, h - margin)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#121212"))

        textPaint.textSize = 80f
        canvas.drawText("Energy: $score", cx, 150f, textPaint)

        textPaint.textSize = 40f
        canvas.drawText("+${autoClick}/sec | Click: +$clickPower", cx, 220f, textPaint)

        canvas.drawCircle(cx, cy, mainRadius, buttonPaint)

        val cost1 = getUpgradeCost(baseUpgradeCost, clickPower)
        upgradePaint.color = if (score >= cost1) Color.parseColor("#03DAC5") else Color.GRAY
        canvas.drawRoundRect(upg1Rect, 20f, 20f, upgradePaint)
        canvas.drawText("Upgrade Click (Cost: $cost1)", cx, upg1Rect.centerY() + 15f, textPaint)

        val cost2 = getUpgradeCost(baseAutoCost, autoClick + 1)
        upgradePaint.color = if (score >= cost2) Color.parseColor("#CF6679") else Color.GRAY
        canvas.drawRoundRect(upg2Rect, 20f, 20f, upgradePaint)
        canvas.drawText("Upgrade Auto (Cost: $cost2)", cx, upg2Rect.centerY() + 15f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            if ((x - cx).pow(2) + (y - cy).pow(2) <= mainRadius.pow(2)) {
                score += clickPower
                mainRadius = 180f
                postDelayed({ mainRadius = 200f; invalidate() }, 100)
            }

            val cost1 = getUpgradeCost(baseUpgradeCost, clickPower)
            if (upg1Rect.contains(x, y) && score >= cost1) {
                score -= cost1
                clickPower++
            }

            val cost2 = getUpgradeCost(baseAutoCost, autoClick + 1)
            if (upg2Rect.contains(x, y) && score >= cost2) {
                score -= cost2
                autoClick++
            }

            invalidate()
        }
        return true
    }

    private fun getUpgradeCost(base: Long, level: Long): Long {
        return (base * 1.5.pow(level.toDouble())).toLong()
    }

    fun saveProgress() {
        prefs.edit()
            .putLong("score", score)
            .putLong("clickPower", clickPower)
            .putLong("autoClick", autoClick)
            .apply()
    }
}
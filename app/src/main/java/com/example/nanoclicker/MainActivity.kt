package com.example.nanoclicker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }
}

@SuppressLint("ViewConstructor")
class GameView(context: Context) : View(context) {

    private var score = 0L
    private var clickPower = 1L
    private var autoClickPower = 0L
    private var upgradeClickCost = 10L
    private var upgradeAutoCost = 50L

    private val stageNames = arrayOf(
        "Asteroid", "Mercury", "Venus", "Earth",
        "Mars", "Gas Giant", "Star", "Pulsar", "Black Hole"
    )
    private var currentPlanetRadius = 200f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }

    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#333333") }

    private val stars = Array(150) {
        floatArrayOf(
            Random.nextFloat(), Random.nextFloat(),
            Random.nextFloat() * 2f + 1f,
            Random.nextFloat() * 10f, Random.nextFloat() * 2f + 0.5f
        )
    }
    private val particles = mutableListOf<Particle>()
    private val floatingTexts = mutableListOf<FloatingText>()

    private var lastTime = 0L

    private val autoClickRunnable = object : Runnable {
        override fun run() {
            if (autoClickPower > 0) {
                score += autoClickPower
                spawnFloatingText("+ $autoClickPower", width / 2f, height / 2.5f - currentPlanetRadius, Color.YELLOW)
                invalidate()
            }
            postDelayed(this, 1000)
        }
    }

    init {
        setBackgroundColor(Color.parseColor("#0a0a1a"))
        post(autoClickRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentTime = System.currentTimeMillis()
        if (lastTime == 0L) lastTime = currentTime
        val dt = (currentTime - lastTime) / 1000f
        lastTime = currentTime

        val cx = width / 2f
        val cy = height / 2.5f

        stars.forEach { star ->
            star[1] += dt * 0.005f * star[2]
            star[1] %= 1f
            val twinkle = (sin(currentTime / 150.0 * star[4] + star[3]) * 100 + 155).toInt()
            starPaint.alpha = twinkle.coerceIn(0, 255)
            canvas.drawCircle(star[0] * width, star[1] * height, star[2], starPaint)
        }
        starPaint.alpha = 255

        val stage = minOf(((clickPower - 1) / 5).toInt(), 8)
        val pulse = sin(currentTime / 200.0).toFloat() * (5f + stage * 2f)
        currentPlanetRadius = 150f + (stage * 10f) + pulse

        drawCelestialBody(canvas, cx, cy, currentPlanetRadius, stage, currentTime)

        val particleIter = particles.iterator()
        while (particleIter.hasNext()) {
            val p = particleIter.next()
            p.update(dt)
            if (p.life <= 0) particleIter.remove()
            else {
                starPaint.alpha = (p.life * 255).toInt()
                canvas.drawCircle(p.x, p.y, p.radius, starPaint)
            }
        }
        starPaint.alpha = 255

        val textIter = floatingTexts.iterator()
        while (textIter.hasNext()) {
            val ft = textIter.next()
            ft.update(dt)
            if (ft.life <= 0) textIter.remove()
            else {
                textPaint.color = ft.color
                textPaint.alpha = (ft.life * 255).toInt()
                canvas.drawText(ft.text, ft.x, ft.y, textPaint)
            }
        }
        textPaint.alpha = 255
        textPaint.color = Color.WHITE

        textPaint.textSize = 70f
        canvas.drawText("Energy: $score", cx, 120f, textPaint)
        textPaint.textSize = 40f
        textPaint.color = Color.CYAN
        canvas.drawText("Stage: ${stageNames[stage]}", cx, 180f, textPaint)
        textPaint.color = Color.WHITE

        val btnY = height - 300f
        canvas.drawRect(50f, btnY, width / 2f - 25f, btnY + 150f, buttonPaint)
        textPaint.textSize = 35f
        canvas.drawText("Power: $clickPower", width / 4f + 12f, btnY + 60f, textPaint)
        canvas.drawText("Cost: $upgradeClickCost", width / 4f + 12f, btnY + 120f, textPaint)

        canvas.drawRect(width / 2f + 25f, btnY, width - 50f, btnY + 150f, buttonPaint)
        canvas.drawText("Auto: $autoClickPower/s", width * 0.75f - 12f, btnY + 60f, textPaint)
        canvas.drawText("Cost: $upgradeAutoCost", width * 0.75f - 12f, btnY + 120f, textPaint)

        invalidate()
    }

    private fun drawCelestialBody(canvas: Canvas, cx: Float, cy: Float, r: Float, stage: Int, time: Long) {
        planetPaint.style = Paint.Style.FILL
        when (stage) {
            0 -> { // Asteroid
                planetPaint.color = Color.parseColor("#888888")
                canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#555555")
                canvas.drawCircle(cx - r*0.3f, cy - r*0.2f, r*0.2f, planetPaint)
                canvas.drawCircle(cx + r*0.4f, cy + r*0.3f, r*0.25f, planetPaint)
            }
            1 -> { // Mercury
                planetPaint.color = Color.parseColor("#8B4513")
                canvas.drawCircle(cx, cy, r, planetPaint)
            }
            2 -> { // Venus
                planetPaint.color = Color.parseColor("#DAA520")
                canvas.drawCircle(cx, cy, r, planetPaint)
            }
            3 -> { // Earth
                planetPaint.color = Color.parseColor("#1E90FF")
                canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#32CD32")
                canvas.drawCircle(cx - r*0.2f, cy + r*0.2f, r*0.5f, planetPaint)
                canvas.drawCircle(cx + r*0.3f, cy - r*0.3f, r*0.4f, planetPaint)
            }
            4 -> { // Mars
                planetPaint.color = Color.parseColor("#B22222")
                canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#800000")
                canvas.drawCircle(cx, cy + r*0.4f, r*0.3f, planetPaint)
            }
            5 -> { // Gas Giant
                planetPaint.color = Color.parseColor("#F4A460")
                canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#CD853F")
                canvas.drawRect(cx - r, cy - r*0.2f, cx + r, cy + r*0.2f, planetPaint)
                planetPaint.style = Paint.Style.STROKE
                planetPaint.strokeWidth = 15f
                planetPaint.color = Color.parseColor("#DEB887")
                canvas.drawOval(cx - r*1.6f, cy - r*0.3f, cx + r*1.6f, cy + r*0.3f, planetPaint)
                planetPaint.style = Paint.Style.FILL
            }
            6 -> { // Star
                planetPaint.color = Color.parseColor("#FF8C00")
                planetPaint.alpha = 100
                canvas.drawCircle(cx, cy, r * 1.3f + sin(time/100.0).toFloat()*15f, planetPaint)
                planetPaint.alpha = 255
                planetPaint.color = Color.parseColor("#FFD700")
                canvas.drawCircle(cx, cy, r, planetPaint)
            }
            7 -> { // Pulsar
                planetPaint.color = Color.parseColor("#00FFFF")
                planetPaint.alpha = 150
                canvas.drawRect(cx - r*0.2f, cy - r*4f, cx + r*0.2f, cy + r*4f, planetPaint)
                planetPaint.alpha = 255
                canvas.drawCircle(cx, cy, r * 0.7f, planetPaint)
            }
            8 -> { // Black Hole
                planetPaint.color = Color.parseColor("#FF4500")
                canvas.drawOval(cx - r*2.5f, cy - r*0.5f, cx + r*2.5f, cy + r*0.5f, planetPaint)
                planetPaint.color = Color.parseColor("#FFA500")
                canvas.drawOval(cx - r*2f, cy - r*0.3f, cx + r*2f, cy + r*0.3f, planetPaint)
                planetPaint.color = Color.BLACK
                canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.WHITE
                planetPaint.style = Paint.Style.STROKE
                planetPaint.strokeWidth = 5f
                canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.style = Paint.Style.FILL
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val cx = width / 2f
            val cy = height / 2.5f

            val dx = event.x - cx
            val dy = event.y - cy
            if (dx * dx + dy * dy <= currentPlanetRadius * currentPlanetRadius) {
                score += clickPower
                spawnParticles(event.x, event.y)
                spawnFloatingText("+$clickPower", event.x, event.y - 50f, Color.WHITE)
                return true
            }

            val btnY = height - 300f
            if (event.y in btnY..(btnY + 150f)) {
                if (event.x in 50f..(width / 2f - 25f)) {
                    if (score >= upgradeClickCost) {
                        score -= upgradeClickCost
                        clickPower++
                        upgradeClickCost = (upgradeClickCost * 1.5).toLong()
                        spawnFloatingText("Upgraded!", event.x, event.y - 20f, Color.GREEN)
                    }
                } else if (event.x in (width / 2f + 25f)..(width - 50f)) {
                    if (score >= upgradeAutoCost) {
                        score -= upgradeAutoCost
                        autoClickPower++
                        upgradeAutoCost = (upgradeAutoCost * 1.8).toLong()
                        spawnFloatingText("Auto Upgraded!", event.x, event.y - 20f, Color.CYAN)
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun spawnParticles(x: Float, y: Float) {
        for (i in 0..10) {
            val angle = Random.nextDouble(Math.PI * 2)
            val speed = Random.nextFloat() * 300f + 100f
            particles.add(
                Particle(
                    x, y,
                    (cos(angle) * speed).toFloat(),
                    (sin(angle) * speed).toFloat()
                )
            )
        }
    }

    private fun spawnFloatingText(text: String, x: Float, y: Float, color: Int) {
        floatingTexts.add(FloatingText(text, x, y, color))
    }

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float) {
        var life = 1f
        var radius = Random.nextFloat() * 10f + 5f
        fun update(dt: Float) {
            x += vx * dt
            y += vy * dt
            vy += 500f * dt
            life -= dt * 2f
        }
    }

    private class FloatingText(val text: String, var x: Float, var y: Float, val color: Int) {
        var life = 1f
        fun update(dt: Float) {
            y -= 150f * dt
            life -= dt * 1.5f
        }
    }
}
package com.example.nanoclicker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioTrack
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

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
    }
}

@SuppressLint("ViewConstructor")
class GameView(context: Context) : View(context), SensorEventListener {

    private var score = 0L
    private var clickPower = 1L
    private var autoClickPower = 0L
    private var drones = 0L

    private var upgradeClickCost = 10L
    private var upgradeAutoCost = 50L
    private var upgradeDroneCost = 500L

    private var darkMatter = 0L
    private var offlineEarnedPending = 0L

    private var isMuted = false

    private val stageNames = arrayOf(
        "Asteroid", "Mercury", "Venus", "Earth",
        "Mars", "Gas Giant", "Star", "Pulsar", "Black Hole"
    )
    private var currentPlanetRadius = 200f
    private var currentStage = 0

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var smoothedTiltX = 0f
    private var smoothedTiltY = 0f

    private val clickSound: AudioTrack
    private val upgradeSound: AudioTrack
    private val prestigeSound: AudioTrack

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 60f; textAlign = Paint.Align.CENTER }
    private val planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#222233") }

    private val stars = Array(150) {
        floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2f + 1f, Random.nextFloat() * 10f, Random.nextFloat() * 2f + 0.5f)
    }
    private val particles = mutableListOf<Particle>()
    private val floatingTexts = mutableListOf<FloatingText>()

    private var lastTime = 0L

    private val autoClickRunnable = object : Runnable {
        override fun run() {
            val totalAuto = autoClickPower + (drones * 15L)
            if (totalAuto > 0) {
                val earned = totalAuto * (1L + darkMatter)
                score += earned
                spawnFloatingText("+ $earned", width / 2f, height / 2.5f - currentPlanetRadius, Color.YELLOW)
                invalidate()
            }
            postDelayed(this, 1000)
        }
    }

    init {
        setBackgroundColor(Color.parseColor("#0a0a1a"))
        clickSound = generateSynthWave(0.05, 800.0, 400.0)
        upgradeSound = generateSynthWave(0.2, 300.0, 1200.0)
        prestigeSound = generateSynthWave(0.8, 1000.0, 50.0)

        loadGame()
        post(autoClickRunnable)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) saveGame()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager.unregisterListener(this)
        saveGame()
        clickSound.release()
        upgradeSound.release()
        prestigeSound.release()
    }

    private fun saveGame() {
        context.getSharedPreferences("NanoSave", Context.MODE_PRIVATE).edit().apply {
            putLong("score", score)
            putLong("clickPower", clickPower)
            putLong("autoClickPower", autoClickPower)
            putLong("drones", drones)
            putLong("upgradeClickCost", upgradeClickCost)
            putLong("upgradeAutoCost", upgradeAutoCost)
            putLong("upgradeDroneCost", upgradeDroneCost)
            putLong("darkMatter", darkMatter)
            putBoolean("isMuted", isMuted)
            putLong("lastTimeSaved", System.currentTimeMillis())
            apply()
        }
    }

    private fun loadGame() {
        val prefs = context.getSharedPreferences("NanoSave", Context.MODE_PRIVATE)
        score = prefs.getLong("score", 0L)
        clickPower = prefs.getLong("clickPower", 1L)
        autoClickPower = prefs.getLong("autoClickPower", 0L)
        drones = prefs.getLong("drones", 0L)
        upgradeClickCost = prefs.getLong("upgradeClickCost", 10L)
        upgradeAutoCost = prefs.getLong("upgradeAutoCost", 50L)
        upgradeDroneCost = prefs.getLong("upgradeDroneCost", 500L)
        darkMatter = prefs.getLong("darkMatter", 0L)
        isMuted = prefs.getBoolean("isMuted", false)

        val lastTimeSaved = prefs.getLong("lastTimeSaved", 0L)
        if (lastTimeSaved > 0) {
            val secondsOffline = (System.currentTimeMillis() - lastTimeSaved) / 1000L
            if (secondsOffline > 0) {
                val autoRate = autoClickPower + (drones * 15L)
                val earned = secondsOffline * autoRate * (1L + darkMatter)
                if (earned > 0) {
                    score += earned
                    offlineEarnedPending = earned
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            smoothedTiltX += 0.1f * (-event.values[0] * 0.3f - smoothedTiltX)
            smoothedTiltY += 0.1f * (event.values[1] * 0.3f - smoothedTiltY)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun generateSynthWave(durationSec: Double, startFreq: Double, endFreq: Double): AudioTrack {
        val sampleRate = 44100
        val numSamples = (durationSec * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * progress
            buffer[i] = (sin(2.0 * Math.PI * i / (sampleRate / currentFreq)) * 15000).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        track.write(buffer, 0, buffer.size)
        return track
    }

    private fun playSound(track: AudioTrack) {
        if (isMuted) return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        track.reloadStaticData()
        track.play()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentTime = System.currentTimeMillis()
        if (lastTime == 0L) lastTime = currentTime
        val dt = (currentTime - lastTime) / 1000f
        lastTime = currentTime

        if (offlineEarnedPending > 0 && width > 0) {
            spawnFloatingText("OFFLINE: +$offlineEarnedPending", width / 2f, height / 2f, Color.CYAN)
            offlineEarnedPending = 0L
        }

        val cx = width / 2f + smoothedTiltX * 10f
        val cy = height / 2.5f + smoothedTiltY * 10f

        stars.forEach { star ->
            star[1] += dt * 0.005f * star[2]
            star[1] %= 1f
            val pX = (star[0] * width + smoothedTiltX * 8f * star[2]) % width
            val pY = (star[1] * height + smoothedTiltY * 8f * star[2]) % height
            val finalX = if (pX < 0) pX + width else pX
            val finalY = if (pY < 0) pY + height else pY

            val twinkle = (sin(currentTime / 150.0 * star[4] + star[3]) * 100 + 155).toInt()
            starPaint.alpha = twinkle.coerceIn(0, 255)
            canvas.drawCircle(finalX, finalY, star[2], starPaint)
        }
        starPaint.alpha = 255

        currentStage = minOf(((clickPower - 1) / 5).toInt(), 8)
        val pulse = sin(currentTime / 200.0).toFloat() * (5f + currentStage * 2f)
        currentPlanetRadius = 150f + (currentStage * 10f) + pulse
        drawCelestialBody(canvas, cx, cy, currentPlanetRadius, currentStage, currentTime)

        for (i in 0 until drones) {
            val angle = (currentTime / 1500.0) + (i * (2 * Math.PI / drones))
            val orbitRadius = currentPlanetRadius + 60f + sin(currentTime / 500.0 + i).toFloat() * 10f
            val droneX = cx + (cos(angle) * orbitRadius).toFloat()
            val droneY = cy + (sin(angle) * orbitRadius).toFloat()

            planetPaint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(droneX - 12f, droneY - 4f, droneX + 12f, droneY + 4f, planetPaint)
            planetPaint.color = Color.parseColor("#FF4444")
            canvas.drawCircle(droneX, droneY, 4f, planetPaint)

            if ((currentTime / 200L) % 10L == i % 10L) {
                planetPaint.color = Color.parseColor("#00FF00")
                planetPaint.strokeWidth = 4f
                canvas.drawLine(droneX, droneY, cx, cy, planetPaint)
                planetPaint.strokeWidth = 0f
            }
        }

        val particleIter = particles.iterator()
        while (particleIter.hasNext()) {
            val p = particleIter.next()
            p.update(dt)
            if (p.life <= 0) particleIter.remove()
            else {
                starPaint.alpha = (p.life * 255).toInt()
                canvas.drawCircle(p.x + smoothedTiltX * 5f, p.y + smoothedTiltY * 5f, p.radius, starPaint)
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
        canvas.drawText("Energy: $score", width / 2f, 120f, textPaint)

        textPaint.textSize = 40f
        textPaint.color = Color.CYAN
        canvas.drawText("Stage: ${stageNames[currentStage]}", width / 2f, 180f, textPaint)

        if (darkMatter > 0) {
            textPaint.color = Color.MAGENTA
            canvas.drawText("Dark Matter: x${darkMatter + 1}", width / 2f, 230f, textPaint)
        }
        textPaint.color = Color.WHITE

        canvas.drawRect(width - 220f, 60f, width - 40f, 140f, buttonPaint)
        textPaint.textSize = 35f
        textPaint.color = if (isMuted) Color.parseColor("#FF5555") else Color.parseColor("#55FF55")
        canvas.drawText(if (isMuted) "SND: OFF" else "SND: ON", width - 130f, 112f, textPaint)
        textPaint.color = Color.WHITE

        if (currentStage == 8) {
            planetPaint.color = Color.MAGENTA
            canvas.drawRect(width / 2f - 200f, height / 2.5f - 300f, width / 2f + 200f, height / 2.5f - 220f, planetPaint)
            textPaint.textSize = 45f
            textPaint.color = Color.WHITE
            canvas.drawText("COLLAPSE UNIVERSE", width / 2f, height / 2.5f - 245f, textPaint)
        }

        val margin = 20f
        val btnW = (width - margin * 4) / 3f
        val btnY = height - 300f

        val x1 = margin
        canvas.drawRect(x1, btnY, x1 + btnW, btnY + 180f, buttonPaint)
        textPaint.textSize = 30f
        canvas.drawText("Power: $clickPower", x1 + btnW / 2f, btnY + 70f, textPaint)
        textPaint.color = Color.YELLOW
        canvas.drawText("$upgradeClickCost E", x1 + btnW / 2f, btnY + 130f, textPaint)
        textPaint.color = Color.WHITE

        val x2 = margin * 2 + btnW
        canvas.drawRect(x2, btnY, x2 + btnW, btnY + 180f, buttonPaint)
        canvas.drawText("Auto: $autoClickPower", x2 + btnW / 2f, btnY + 70f, textPaint)
        textPaint.color = Color.YELLOW
        canvas.drawText("$upgradeAutoCost E", x2 + btnW / 2f, btnY + 130f, textPaint)
        textPaint.color = Color.WHITE

        val x3 = margin * 3 + btnW * 2
        canvas.drawRect(x3, btnY, x3 + btnW, btnY + 180f, buttonPaint)
        canvas.drawText("Probe: $drones", x3 + btnW / 2f, btnY + 70f, textPaint)
        textPaint.color = Color.YELLOW
        canvas.drawText("$upgradeDroneCost E", x3 + btnW / 2f, btnY + 130f, textPaint)

        invalidate()
    }

    private fun drawCelestialBody(canvas: Canvas, cx: Float, cy: Float, r: Float, stage: Int, time: Long) {
        planetPaint.style = Paint.Style.FILL
        when (stage) {
            0 -> {
                planetPaint.color = Color.parseColor("#888888"); canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#555555"); canvas.drawCircle(cx - r*0.3f, cy - r*0.2f, r*0.2f, planetPaint); canvas.drawCircle(cx + r*0.4f, cy + r*0.3f, r*0.25f, planetPaint)
            }
            1 -> { planetPaint.color = Color.parseColor("#8B4513"); canvas.drawCircle(cx, cy, r, planetPaint) }
            2 -> { planetPaint.color = Color.parseColor("#DAA520"); canvas.drawCircle(cx, cy, r, planetPaint) }
            3 -> {
                planetPaint.color = Color.parseColor("#1E90FF"); canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#32CD32"); canvas.drawCircle(cx - r*0.2f, cy + r*0.2f, r*0.5f, planetPaint); canvas.drawCircle(cx + r*0.3f, cy - r*0.3f, r*0.4f, planetPaint)
            }
            4 -> {
                planetPaint.color = Color.parseColor("#B22222"); canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#800000"); canvas.drawCircle(cx, cy + r*0.4f, r*0.3f, planetPaint)
            }
            5 -> {
                planetPaint.color = Color.parseColor("#F4A460"); canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.parseColor("#CD853F"); canvas.drawRect(cx - r, cy - r*0.2f, cx + r, cy + r*0.2f, planetPaint)
                planetPaint.style = Paint.Style.STROKE; planetPaint.strokeWidth = 15f; planetPaint.color = Color.parseColor("#DEB887")
                canvas.drawOval(cx - r*1.6f, cy - r*0.3f, cx + r*1.6f, cy + r*0.3f, planetPaint); planetPaint.style = Paint.Style.FILL
            }
            6 -> {
                planetPaint.color = Color.parseColor("#FF8C00"); planetPaint.alpha = 100
                canvas.drawCircle(cx, cy, r * 1.3f + sin(time/100.0).toFloat()*15f, planetPaint); planetPaint.alpha = 255
                planetPaint.color = Color.parseColor("#FFD700"); canvas.drawCircle(cx, cy, r, planetPaint)
            }
            7 -> {
                planetPaint.color = Color.parseColor("#00FFFF"); planetPaint.alpha = 150
                canvas.drawRect(cx - r*0.2f, cy - r*4f, cx + r*0.2f, cy + r*4f, planetPaint); planetPaint.alpha = 255
                canvas.drawCircle(cx, cy, r * 0.7f, planetPaint)
            }
            8 -> {
                planetPaint.color = Color.parseColor("#FF4500"); canvas.drawOval(cx - r*2.5f, cy - r*0.5f, cx + r*2.5f, cy + r*0.5f, planetPaint)
                planetPaint.color = Color.parseColor("#FFA500"); canvas.drawOval(cx - r*2f, cy - r*0.3f, cx + r*2f, cy + r*0.3f, planetPaint)
                planetPaint.color = Color.BLACK; canvas.drawCircle(cx, cy, r, planetPaint)
                planetPaint.color = Color.WHITE; planetPaint.style = Paint.Style.STROKE; planetPaint.strokeWidth = 5f
                canvas.drawCircle(cx, cy, r, planetPaint); planetPaint.style = Paint.Style.FILL
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {

            if (currentStage == 8 && event.y in (height / 2.5f - 300f)..(height / 2.5f - 220f)) {
                if (event.x in (width / 2f - 200f)..(width / 2f + 200f)) {
                    darkMatter++
                    score = 0
                    clickPower = 1
                    autoClickPower = 0
                    drones = 0
                    upgradeClickCost = 10
                    upgradeAutoCost = 50
                    upgradeDroneCost = 500

                    playSound(prestigeSound)
                    spawnParticles(width / 2f, height / 2.5f, 50)
                    spawnFloatingText("UNIVERSE RESET", width / 2f, height / 2f, Color.MAGENTA)
                    saveGame()
                    return true
                }
            }

            if (event.x in (width - 220f)..(width - 40f) && event.y in 60f..140f) {
                isMuted = !isMuted
                saveGame()
                return true
            }

            val cx = width / 2f + smoothedTiltX * 10f
            val cy = height / 2.5f + smoothedTiltY * 10f
            val dx = event.x - cx
            val dy = event.y - cy
            if (dx * dx + dy * dy <= currentPlanetRadius * currentPlanetRadius) {
                val earned = clickPower * (1L + darkMatter)
                score += earned
                playSound(clickSound)
                spawnParticles(event.x, event.y, 10)
                spawnFloatingText("+$earned", event.x, event.y - 50f, Color.WHITE)
                return true
            }

            val margin = 20f
            val btnW = (width - margin * 4) / 3f
            val btnY = height - 300f

            if (event.y in btnY..(btnY + 180f)) {
                if (event.x in margin..(margin + btnW) && score >= upgradeClickCost) {
                    score -= upgradeClickCost
                    clickPower++
                    upgradeClickCost = (upgradeClickCost * 1.5).toLong()
                    playSound(upgradeSound)
                    spawnFloatingText("UPGRADED", event.x, event.y - 20f, Color.GREEN)
                }
                else if (event.x in (margin * 2 + btnW)..(margin * 2 + btnW * 2) && score >= upgradeAutoCost) {
                    score -= upgradeAutoCost
                    autoClickPower++
                    upgradeAutoCost = (upgradeAutoCost * 1.8).toLong()
                    playSound(upgradeSound)
                    spawnFloatingText("AUTO+", event.x, event.y - 20f, Color.CYAN)
                }
                else if (event.x in (margin * 3 + btnW * 2)..(margin * 3 + btnW * 3) && score >= upgradeDroneCost) {
                    score -= upgradeDroneCost
                    drones++
                    upgradeDroneCost = (upgradeDroneCost * 2.5).toLong()
                    playSound(upgradeSound)
                    spawnFloatingText("PROBE DEPLOYED", event.x, event.y - 20f, Color.MAGENTA)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun spawnParticles(x: Float, y: Float, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextDouble(Math.PI * 2)
            val speed = Random.nextFloat() * 300f + 100f
            particles.add(Particle(x, y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat()))
        }
    }

    private fun spawnFloatingText(text: String, x: Float, y: Float, color: Int) {
        floatingTexts.add(FloatingText(text, x, y, color))
    }

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float) {
        var life = 1f
        var radius = Random.nextFloat() * 10f + 5f
        fun update(dt: Float) {
            x += vx * dt; y += vy * dt; vy += 500f * dt; life -= dt * 2f
        }
    }

    private class FloatingText(val text: String, var x: Float, var y: Float, val color: Int) {
        var life = 1f
        fun update(dt: Float) {
            y -= 150f * dt; life -= dt * 1.5f
        }
    }
}
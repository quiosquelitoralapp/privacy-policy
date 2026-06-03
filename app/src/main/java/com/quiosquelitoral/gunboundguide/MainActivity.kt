package com.quiosquelitoral.gunboundguide

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {

    private lateinit var trajectoryView: TrajectoryView
    private lateinit var angleSeekBar: SeekBar
    private lateinit var powerSeekBar: SeekBar
    private lateinit var windSeekBar: SeekBar
    private lateinit var angleValueText: TextView
    private lateinit var powerValueText: TextView
    private lateinit var windValueText: TextView
    private lateinit var directionBtn: Button
    private lateinit var mobileContainer: LinearLayout

    private val mobileButtons = mutableListOf<Button>()
    private var selectedMobileIndex = 0
    private var facingRight = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        trajectoryView  = findViewById(R.id.trajectoryView)  as TrajectoryView
        angleSeekBar    = findViewById(R.id.seekAngle)        as SeekBar
        powerSeekBar    = findViewById(R.id.seekPower)        as SeekBar
        windSeekBar     = findViewById(R.id.seekWind)         as SeekBar
        angleValueText  = findViewById(R.id.tvAngleValue)     as TextView
        powerValueText  = findViewById(R.id.tvPowerValue)     as TextView
        windValueText   = findViewById(R.id.tvWindValue)      as TextView
        directionBtn    = findViewById(R.id.btnDirection)     as Button
        mobileContainer = findViewById(R.id.mobileContainer)  as LinearLayout

        setupMobileButtons()
        setupSeekBars()
        setupDirectionButton()
    }

    private fun setupMobileButtons() {
        MobileData.mobiles.forEachIndexed { index, mobile ->
            val btn = Button(this).apply {
                text = mobile.displayName
                textSize = 11f
                setAllCaps(false)
                setPadding(20, 6, 20, 6)
                setOnClickListener { selectMobile(index) }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 4, 4, 4) }
            btn.layoutParams = params
            mobileButtons.add(btn)
            mobileContainer.addView(btn)
        }
        selectMobile(0)
    }

    private fun selectMobile(index: Int) {
        selectedMobileIndex = index
        mobileButtons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.setBackgroundColor(Color.parseColor("#00FF88"))
                btn.setTextColor(Color.BLACK)
            } else {
                btn.setBackgroundColor(Color.parseColor("#1E3A5A"))
                btn.setTextColor(Color.WHITE)
            }
        }
        trajectoryView.selectedMobile = MobileData.mobiles[index]
    }

    private fun setupSeekBars() {
        angleSeekBar.max = 90
        angleSeekBar.progress = 45
        powerSeekBar.max = 100
        powerSeekBar.progress = 50
        windSeekBar.max = 20
        windSeekBar.progress = 10

        updateAllLabels()

        angleSeekBar.setOnSeekBarChangeListener(onChange { progress ->
            trajectoryView.angle = progress.toFloat()
            angleValueText.text = "$progress°"
        })
        powerSeekBar.setOnSeekBarChangeListener(onChange { progress ->
            trajectoryView.power = progress.toFloat()
            powerValueText.text = "$progress"
        })
        windSeekBar.setOnSeekBarChangeListener(onChange { progress ->
            val wind = (progress - 10).toFloat()
            trajectoryView.windSpeed = wind
            windValueText.text = formatWind(wind)
        })
    }

    private fun setupDirectionButton() {
        updateDirectionButton()
        directionBtn.setOnClickListener {
            facingRight = !facingRight
            trajectoryView.facingRight = facingRight
            updateDirectionButton()
        }
    }

    private fun updateDirectionButton() {
        directionBtn.text = if (facingRight) "→  DIREITA" else "←  ESQUERDA"
    }

    private fun updateAllLabels() {
        angleValueText.text = "${angleSeekBar.progress}°"
        powerValueText.text = "${powerSeekBar.progress}"
        windValueText.text = formatWind((windSeekBar.progress - 10).toFloat())
    }

    private fun formatWind(wind: Float): String = when {
        wind > 0f  -> "→ ${wind.toInt()}"
        wind < 0f  -> "← ${(-wind).toInt()}"
        else       -> "0"
    }

    private fun onChange(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) = block(progress)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }
}

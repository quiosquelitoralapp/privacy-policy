package com.quiosquelitoral.gunboundguide

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

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

        trajectoryView  = findViewById(R.id.trajectoryView)
        angleSeekBar    = findViewById(R.id.seekAngle)
        powerSeekBar    = findViewById(R.id.seekPower)
        windSeekBar     = findViewById(R.id.seekWind)
        angleValueText  = findViewById(R.id.tvAngleValue)
        powerValueText  = findViewById(R.id.tvPowerValue)
        windValueText   = findViewById(R.id.tvWindValue)
        directionBtn    = findViewById(R.id.btnDirection)
        mobileContainer = findViewById(R.id.mobileContainer)

        setupMobileButtons()
        setupSeekBars()
        setupDirectionButton()
    }

    private fun setupMobileButtons() {
        MobileData.mobiles.forEachIndexed { index, mobile ->
            val btn = Button(this).apply {
                text = mobile.displayName
                textSize = 11f
                isAllCaps = false
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
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_green))
                btn.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                btn.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_bg))
                btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
        }
        trajectoryView.selectedMobile = MobileData.mobiles[index]
    }

    private fun setupSeekBars() {
        angleSeekBar.max = 90
        angleSeekBar.progress = 45

        powerSeekBar.max = 100
        powerSeekBar.progress = 50

        // Wind: 0..20, onde 10 = sem vento
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

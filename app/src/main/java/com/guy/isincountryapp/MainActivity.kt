package com.guy.isincountryapp

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.guy.isincountrylibrary.CountryLocator
import com.guy.isincountrylibrary.inWhichCountry
import com.guy.isincountrylibrary.isInCountry
import com.guy.isincountrylibrary.isInSea

class MainActivity : AppCompatActivity() {

    private data class SamplePoint(
        val label: String,
        val lat: Double,
        val lon: Double,
        val isoCode: String
    )

    private val presets = listOf(
        SamplePoint("Tel Aviv", 32.0853, 34.7818, "IL"),
        SamplePoint("Mediterranean Sea", 32.0853, 34.0000, "IL"),
        SamplePoint("Paris", 48.8566, 2.3522, "FR"),
        SamplePoint("New York", 40.7128, -74.0060, "US"),
        SamplePoint("Lesotho (Hole in ZA)", -29.3151, 27.4869, "ZA"),
        SamplePoint("Atlantic Ocean", 0.0000, -30.0000, "US")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        CountryLocator.init(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etLatitude = findViewById<TextInputEditText>(R.id.etLatitude)
        val etLongitude = findViewById<TextInputEditText>(R.id.etLongitude)
        val etIsoCode = findViewById<TextInputEditText>(R.id.etIsoCode)
        val btnCheck = findViewById<MaterialButton>(R.id.btnCheck)
        val tvResults = findViewById<TextView>(R.id.tvResults)
        val chipGroup = findViewById<ChipGroup>(R.id.presetChipGroup)

        fun evaluateCurrentInputs() {
            val lat = etLatitude.text?.toString()?.trim()?.toDoubleOrNull()
            val lon = etLongitude.text?.toString()?.trim()?.toDoubleOrNull()
            val iso = etIsoCode.text?.toString()?.trim().orEmpty()

            if (lat == null || lon == null) {
                tvResults.text = "Please enter valid numeric latitude and longitude."
                return
            }

            val inTargetCountry = isInCountry(lat, lon, iso)
            val countryIso = inWhichCountry(lat, lon)
            val inSea = isInSea(lat, lon)
            val info = CountryLocator.getCountryInfo(lat, lon)

            tvResults.text = buildString {
                appendLine("isInCountry($lat, $lon, \"$iso\")")
                appendLine("  -> $inTargetCountry")
                appendLine()
                appendLine("inWhichCountry($lat, $lon)")
                appendLine("  -> ${countryIso ?: "null"}${info?.let { " (${it.name})" } ?: ""}")
                appendLine()
                appendLine("isInSea($lat, $lon)")
                append("  -> $inSea")
            }
        }

        for ((index, sample) in presets.withIndex()) {
            val chip = Chip(this).apply {
                text = sample.label
                isCheckable = true
                if (index == 0) isChecked = true
                setOnClickListener {
                    etLatitude.setText(sample.lat.toString())
                    etLongitude.setText(sample.lon.toString())
                    etIsoCode.setText(sample.isoCode)
                    evaluateCurrentInputs()
                }
            }
            chipGroup.addView(chip)
        }

        btnCheck.setOnClickListener { evaluateCurrentInputs() }
        evaluateCurrentInputs()
    }
}

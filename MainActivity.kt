package com.example.prognosismeteo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6F9)) {
                    WeatherAppScreen()
                }
            }
        }
    }
}

// Modele de date pentru Geocoding API
@Serializable
data class GeocodingResponse(val results: List<GeoLocation>? = null)

@Serializable
data class GeoLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null // Regiunea/Județul
)

// Modele de date pentru Weather API
@Serializable
data class WeatherResponse(val daily: DailyData)

@Serializable
data class DailyData(
    val time: List<String>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val weather_code: List<Int>
)

data class DayForecast(
    val date: String,
    val dayOfWeek: String,
    val tempMin: Int,
    val tempMax: Int,
    val condition: String,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherAppScreen() {
    var cityInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentCityForecast by remember { mutableStateOf<List<DayForecast>?>(null) }
    var resolvedLocationName by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Prognoză Globală 7 Zile", 
            fontSize = 24.sp, 
            fontWeight = FontWeight.Bold, 
            color = Color(0xFF1E293B), 
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cityInput,
                onValueChange = { cityInput = it },
                label = { Text("Introdu localitatea (ex: Zarnesti sau Paris)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (cityInput.isNotBlank()) {
                        isLoading = true
                        errorMessage = null
                        resolvedLocationName = null
                        coroutineScope.launch {
                            try {
                                val client = HttpClient {
                                    install(ContentNegotiation) {
                                        json(Json { ignoreUnknownKeys = true })
                                    }
                                }
                                
                                // Pasul 1: Geocoding - Transformă textul în coordonate
                                val geoUrl = "https://open-meteo.com{cityInput.trim()}&count=1&language=ro"
                                val geoResponse: GeocodingResponse = client.get(geoUrl).body()
                                
                                val location = geoResponse.results?.firstOrNull()
                                
                                if (location != null) {
                                    resolvedLocationName = "${location.name}, ${location.admin1 ?: ""}, ${location.country}"
                                    
                                    // Pasul 2: Weather - Descarcă prognoza pe baza coordonatelor găsite
                                    val weatherUrl = "https://open-meteo.com{location.latitude}&longitude=${location.longitude}&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto"
                                    val weatherResponse: WeatherResponse = client.get(weatherUrl).body()
                                    
                                    currentCityForecast = processWeatherData(weatherResponse)
                                } else {
                                    errorMessage = "Localitatea nu a fost găsită. Încearcă altă denumire."
                                }
                                client.close()
                            } catch (e: Exception) {
                                errorMessage = "Eroare de rețea. Verifică conexiunea la internet."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Caută")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
        }

        resolvedLocationName?.let {
            Text(
                text = "Rezultate pentru: $it",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF2563EB))
            }
        } else {
            currentCityForecast?.let { forecasts ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(forecasts) { day ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp), 
                                horizontalArrangement = Arrangement.SpaceBetween, 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = day.dayOfWeek, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(text = day.date, fontSize = 12.sp, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = day.icon, fontSize = 28.sp, modifier = Modifier.padding(end = 8.dp))
                                    Text(text = day.condition, fontSize = 14.sp, color = Color.DarkGray)
                                }
                                Text(text = "${day.tempMax}°C / ${day.tempMin}°C", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Procesarea datelor brute din API în formatul necesar listei din interfață
fun processWeatherData(response: WeatherResponse): List<DayForecast> {
    val parsedList = mutableListOf<DayForecast>()
    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val outputFormat = SimpleDateFormat("dd MMM", Locale("ro"))
    val dayFormat = SimpleDateFormat("EEEE", Locale("ro"))

    val daily = response.daily
    for (i in daily.time.indices) {
        val dateObj = inputFormat.parse(daily.time[i]) ?: Date()
        val dateStr = outputFormat.format(dateObj)
        var dayName = dayFormat.format(dateObj).replaceFirstChar { it.uppercase() }
        
        if (i == 0) dayName = "Azi"
        if (i == 1) dayName = "Mâine"

        val (conditionText, iconText) = parseWeatherCode(daily.weather_code[i])

        parsedList.add(
            DayForecast(
                date = dateStr,
                dayOfWeek = dayName,
                tempMin = daily.temperature_2m_min[i].toInt(),
                tempMax = daily.temperature_2m_max[i].toInt(),
                condition = conditionText,
                icon = iconText
            )
        )
    }
    return parsedList
}

fun parseWeatherCode(code: Int): Pair<String, String> {
    return wmoCodeMap[code] ?: Pair("Cer Schimbător", "⛅")
}

val wmoCodeMap = mapOf(
    0 to Pair("Senin", "☀️"),

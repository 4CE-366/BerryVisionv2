package com.example.berryvision.ui.stats

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyCount(val date: String, val count: Int)

class DailyCountViewModel : ViewModel() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // Almacenamiento básico para la demostración
    private val _dailyCounts = MutableStateFlow<List<DailyCount>>(
        listOf(
            DailyCount("2026-07-13", 120),
            DailyCount("2026-07-14", 345),
            DailyCount(dateFormat.format(Date()), 45) // Conteo de hoy
        )
    )
    val dailyCounts: StateFlow<List<DailyCount>> = _dailyCounts.asStateFlow()

    fun addStrawberryCount(countToAdd: Int) {
        val today = dateFormat.format(Date())
        val currentList = _dailyCounts.value.toMutableList()
        val todayIndex = currentList.indexOfFirst { it.date == today }

        if (todayIndex != -1) {
            val existing = currentList[todayIndex]
            currentList[todayIndex] = existing.copy(count = existing.count + countToAdd)
        } else {
            currentList.add(DailyCount(today, countToAdd))
        }
        
        // Ordenar por fecha descendente
        currentList.sortByDescending { it.date }
        _dailyCounts.value = currentList
    }
}

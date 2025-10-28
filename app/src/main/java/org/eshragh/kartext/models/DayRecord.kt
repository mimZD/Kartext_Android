package org.eshragh.kartext.models

data class DayRecord(
    val dayOfMonth: Int,
    val dayOfWeek: String,
    val totalHours: Long,
    val totalMinutes: Long,
    val timestamp: Long
)
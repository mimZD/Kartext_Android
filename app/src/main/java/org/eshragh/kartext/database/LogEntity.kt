package org.eshragh.kartext.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.eshragh.kartext.models.RecordType

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey
    val id: String,
    var enterTime: Long,
    var exitTime: Long? = null,
    var deductions: Long = 0,
    val type: RecordType = RecordType.WORK
)

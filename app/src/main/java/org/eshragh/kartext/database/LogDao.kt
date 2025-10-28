package org.eshragh.kartext.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Update
    suspend fun updateLog(log: LogEntity)

    @Query("SELECT * FROM logs ORDER BY enterTime DESC")
    suspend fun getAllLogs(): List<LogEntity>

    @Query("SELECT * FROM logs WHERE enterTime BETWEEN :startTime AND :endTime ORDER BY enterTime DESC")
    suspend fun getLogsBetween(startTime: Long, endTime: Long): List<LogEntity>

    @Query("SELECT * FROM logs WHERE exitTime IS NULL ORDER BY enterTime DESC LIMIT 1")
    suspend fun getLastUnfinishedLog(): LogEntity?

    @Query("DELETE FROM logs WHERE id = :logId")
    suspend fun deleteLogById(logId: Long)

    @Query("DELETE FROM logs")
    suspend fun clearAll()

    @Transaction
    suspend fun clearAndInsert(logs: List<LogEntity>) {
        clearAll()
        logs.forEach { insertLog(it) }
    }
}
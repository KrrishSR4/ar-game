package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {
    @Query("SELECT * FROM game_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<GameRecord>>

    @Query("SELECT MAX(score) FROM game_records WHERE level = :level")
    fun getHighScoreForLevel(level: Int): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GameRecord)

    @Query("DELETE FROM game_records")
    suspend fun clearAllRecords()
}

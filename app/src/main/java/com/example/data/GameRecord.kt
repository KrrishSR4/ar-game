package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val level: Int,
    val score: Int,
    val timeUsed: Int, // seconds
    val coinsCollected: Int,
    val timestamp: Long = System.currentTimeMillis()
)

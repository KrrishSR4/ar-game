package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val dao: GameRecordDao) {
    val allRecords: Flow<List<GameRecord>> = dao.getAllRecords()

    fun getHighScoreForLevel(level: Int): Flow<Int?> = dao.getHighScoreForLevel(level)

    suspend fun insertRecord(record: GameRecord) {
        dao.insertRecord(record)
    }

    suspend fun clearHistory() {
        dao.clearAllRecords()
    }
}

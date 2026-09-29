package com.example.data

import com.example.model.CalculationItem
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<CalculationItem>> = historyDao.getAllHistory()

    suspend fun insert(item: CalculationItem): Long = historyDao.insert(item)

    suspend fun deleteById(id: Long) = historyDao.deleteById(id)

    suspend fun clearAll() = historyDao.clearAll()
}

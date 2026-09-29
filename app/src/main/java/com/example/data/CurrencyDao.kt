package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.CurrencyInfoEntity
import com.example.model.CurrencyRateEntity
import com.example.model.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currency_rates WHERE baseCurrency = :base AND quoteCurrency = :quote LIMIT 1")
    suspend fun getRate(base: String, quote: String): CurrencyRateEntity?

    @Query("SELECT * FROM currency_rates WHERE baseCurrency = :base")
    suspend fun getRatesForBase(base: String): List<CurrencyRateEntity>

    @Query("SELECT * FROM currency_rates")
    suspend fun getAllRates(): List<CurrencyRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(rates: List<CurrencyRateEntity>)

    @Query("SELECT * FROM currencies ORDER BY code ASC")
    fun getAllCurrencies(): Flow<List<CurrencyInfoEntity>>

    @Query("SELECT * FROM currencies ORDER BY code ASC")
    suspend fun getAllCurrenciesList(): List<CurrencyInfoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrencies(currencies: List<CurrencyInfoEntity>)

    @Query("SELECT * FROM sync_metadata WHERE key = :key LIMIT 1")
    suspend fun getMetadata(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: SyncMetadataEntity)

    @Query("SELECT COUNT(*) FROM currency_rates")
    suspend fun getRatesCount(): Int

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun getCurrenciesCount(): Int
}

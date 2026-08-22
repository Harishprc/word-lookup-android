package com.harish.wordlookup.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LookupDao {

    @Query("SELECT * FROM lookups WHERE language = :language AND key = :key LIMIT 1")
    suspend fun get(language: String, key: String): LookupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LookupEntity)

    @Query("SELECT * FROM lookups ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LookupEntity>>

    @Query("DELETE FROM lookups WHERE language = :language AND key = :key")
    suspend fun delete(language: String, key: String)
}

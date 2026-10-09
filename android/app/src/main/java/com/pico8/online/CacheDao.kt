package com.pico8.online

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CacheEntity)

    @Query("SELECT * FROM cache WHERE `key` LIKE :prefix || '%'")
    suspend fun getByPrefix(prefix: String): List<CacheEntity>

    @Query("DELETE FROM cache WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cache WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cache")
    suspend fun count(): Int

    @Query("SELECT SUM(LENGTH(content)) FROM cache")
    suspend fun totalSize(): Long
}

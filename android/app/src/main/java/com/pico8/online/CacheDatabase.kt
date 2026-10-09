package com.pico8.online

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CacheEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(CacheConverters::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var INSTANCE: CacheDatabase? = null

        fun getDatabase(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    "pico8_cache_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

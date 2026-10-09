package com.pico8.online

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "cache")
@TypeConverters(CacheConverters::class)
data class CacheEntity(
    @PrimaryKey val key: String,
    val content: ByteArray,
    val lastModified: Long,
    val etag: String? = null,
    val contentType: String = "application/octet-stream"
)

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "users")
data class LocalUser(
    @PrimaryKey val email: String,
    val fullName: String,
    val pinOrPassword: String,
    val avatarEmoji: String = "👤",
    val colorHex: String = "#3B82F6",
    val createdAt: Long = System.currentTimeMillis()
)

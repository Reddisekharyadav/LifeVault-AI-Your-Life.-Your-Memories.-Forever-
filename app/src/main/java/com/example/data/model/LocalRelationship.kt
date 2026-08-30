package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "relationships")
data class LocalRelationship(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userEmail: String = "",             // Partition key for current logged-in user
    val name: String,
    val relationType: String,               // "FRIEND", "FAMILY", "CLASSMATE", "OTHER"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

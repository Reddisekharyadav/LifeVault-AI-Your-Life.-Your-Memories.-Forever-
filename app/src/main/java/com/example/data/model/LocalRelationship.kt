package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "relationships")
data class LocalRelationship(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relationType: String,               // "FRIEND", "FAMILY", "CLASSMATE", "OTHER"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

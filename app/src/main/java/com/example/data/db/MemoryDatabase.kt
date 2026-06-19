package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship

@Database(entities = [LocalMemory::class, LocalRelationship::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        fun getDatabase(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "lifevault_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

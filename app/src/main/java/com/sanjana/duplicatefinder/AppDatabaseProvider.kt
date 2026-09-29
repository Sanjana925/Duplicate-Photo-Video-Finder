package com.sanjana.duplicatefinder

import android.content.Context
import androidx.room.Room
import com.sanjana.duplicatefinder.database.AppDatabase

object AppDatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(
        context: Context
    ): AppDatabase {

        return INSTANCE
            ?: synchronized(this) {

                INSTANCE
                    ?: Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "duplicate_finder.db"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                        .also {
                            INSTANCE = it
                        }
            }
    }
}
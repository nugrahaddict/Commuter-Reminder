package com.personaldevelopment.commuterreminder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StationEntity::class], version = 2, exportSchema = false)
abstract class CommuterDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao

    companion object {
        @Volatile
        private var instance: CommuterDatabase? = null

        fun get(context: Context): CommuterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CommuterDatabase::class.java,
                    "commuter_reminder.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

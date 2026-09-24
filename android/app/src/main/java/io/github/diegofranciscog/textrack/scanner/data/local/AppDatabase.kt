package io.github.diegofranciscog.textrack.scanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReadingEntity::class, OperatorEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun readings(): ReadingDao

    abstract fun operators(): OperatorDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "textrack-scanner.db").build()
    }
}

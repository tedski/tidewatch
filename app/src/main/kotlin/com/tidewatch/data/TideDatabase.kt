package com.tidewatch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tidewatch.data.dao.HarmonicConstituentDao
import com.tidewatch.data.dao.StationDao
import com.tidewatch.data.dao.SubordinateOffsetDao
import com.tidewatch.data.models.HarmonicConstituent
import com.tidewatch.data.models.Station
import com.tidewatch.data.models.SubordinateOffset
import java.io.File
import java.io.FileOutputStream

/**
 * Room database for tide station data.
 *
 * The database is bundled in the app's assets folder and copied to the
 * app's data directory on first launch.
 */
@Database(
    entities = [
        Station::class,
        HarmonicConstituent::class,
        SubordinateOffset::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TideDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao
    abstract fun harmonicConstituentDao(): HarmonicConstituentDao
    abstract fun subordinateOffsetDao(): SubordinateOffsetDao

    companion object {
        private const val DATABASE_NAME = "tides.db"

        @Volatile
        private var INSTANCE: TideDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * @param context Application context
         * @return TideDatabase instance
         */
        fun getInstance(context: Context): TideDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Build the database, copying from assets if necessary.
         */
        private fun buildDatabase(context: Context): TideDatabase {
            val dbFile = context.getDatabasePath(DATABASE_NAME)

            // Copy database from assets if it doesn't exist
            if (!dbFile.exists()) {
                copyDatabaseFromAssets(context, dbFile)
            }

            return Room.databaseBuilder(
                context.applicationContext,
                TideDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // For development; remove for production
                .build()
        }

        /**
         * Copy the pre-populated database from assets to the app's data directory.
         */
        private fun copyDatabaseFromAssets(context: Context, targetFile: File) {
            targetFile.parentFile?.mkdirs()

            context.assets.open(DATABASE_NAME).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        /**
         * Clear the singleton instance (useful for testing).
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TripEntity::class, GasReceiptEntity::class, TripRecoveryEntity::class, OdometerReadingEntity::class, VehicleExpenseEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun odometerReadingDao(): OdometerReadingDao
    abstract fun vehicleExpenseDao(): VehicleExpenseDao
    abstract fun tripDao(): TripDao
    abstract fun gasReceiptDao(): GasReceiptDao
    abstract fun tripRecoveryDao(): TripRecoveryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_recoveries (recoveryKey TEXT NOT NULL PRIMARY KEY, tripId INTEGER NOT NULL)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS odometer_readings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, vehicle TEXT NOT NULL, recordedAt INTEGER NOT NULL, readingKm REAL NOT NULL, source TEXT NOT NULL, photoPath TEXT, photoReviewed INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_expenses (vehicle TEXT NOT NULL, year INTEGER NOT NULL, openingKm REAL NOT NULL, closingKm REAL NOT NULL, fuel REAL NOT NULL, maintenance REAL NOT NULL, insurance REAL NOT NULL, registration REAL NOT NULL, allowableInterest REAL NOT NULL, allowableLease REAL NOT NULL, businessParking REAL NOT NULL, supplementaryInsurance REAL NOT NULL, PRIMARY KEY(vehicle, year))")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mileage_tracker_db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4).fallbackToDestructiveMigration(false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

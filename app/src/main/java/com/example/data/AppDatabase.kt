package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TripEntity::class, GasReceiptEntity::class, TripRecoveryEntity::class, OdometerReadingEntity::class, VehicleExpenseEntity::class], version = 5, exportSchema = false)
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
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("trips", "gas_receipts")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncId TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncOwner TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncUpdatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncChangeId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN syncDeleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("CREATE UNIQUE INDEX index_${table}_syncId ON $table(syncId)")
                }
                db.execSQL("ALTER TABLE gas_receipts ADD COLUMN tripSyncId TEXT")
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
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).fallbackToDestructiveMigration(false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

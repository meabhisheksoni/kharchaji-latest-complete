package com.example.monday.data.local

import android.content.Context
import com.example.monday.data.models.TodoItem
import com.example.monday.data.models.CalculationRecord
import com.example.monday.data.models.CalculationRecordConverters
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TodoItem::class, CalculationRecord::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(CalculationRecordConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration() // Added as a safety net
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_table ADD COLUMN imageUris TEXT")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create indices for timestamp and isDone columns
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_table_timestamp` ON `todo_table` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_table_isDone` ON `todo_table` (`isDone`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create composite index for timestamp and isDone
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_table_timestamp_isDone` ON `todo_table` (`timestamp`, `isDone`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create indices for reporting performance on calculation_records
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculation_records_recordDate` ON `calculation_records` (`recordDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculation_records_isMasterSave` ON `calculation_records` (`isMasterSave`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calculation_records_recordDate_isMasterSave` ON `calculation_records` (`recordDate`, `isMasterSave`)")
            }
        }
    }
}

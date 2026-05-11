package com.gamegear.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GameEntity::class], version = 3, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {
        @Volatile private var INSTANCE: GameDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE games
                    SET notes = CASE WHEN notes IS NULL OR notes = ''
                        THEN TRIM(SUBSTR(title, INSTR(title, '/') + 1))
                        ELSE TRIM(SUBSTR(title, INSTR(title, '/') + 1)) || char(10) || notes END,
                    title = TRIM(SUBSTR(title, 1, INSTR(title, '/') - 1))
                    WHERE INSTR(title, '/') > 0
                """.trimIndent())
                db.execSQL("UPDATE games SET title = REPLACE(title, '€', '(E)') WHERE title LIKE '%€%'")
                db.execSQL("UPDATE games SET notes = REPLACE(notes, '€', '(E)') WHERE notes LIKE '%€%'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    UPDATE games
                    SET title = 'Mick & Mack as the Global Gladiators'
                    WHERE title = 'Global Gladiators, McDonald''s'
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): GameDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "gamegear.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}

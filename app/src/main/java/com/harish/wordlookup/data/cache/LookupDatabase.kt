package com.harish.wordlookup.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * version 2 added the `createdAt` index; version 3 drops `deleted`/`updatedAt`
 * (see LookupEntity) now that sync - the only reason a delete needed to be a
 * tombstone instead of a real row removal - is gone. version 4 adds
 * `synonymsNative` (round 7 - the register/popup card now shows synonyms in
 * the target language, not just English).
 *
 * [MIGRATION_3_4] is the first real Migration in this app's history - prior
 * bumps relied solely on [fallbackToDestructiveMigration], which was an
 * honest tradeoff while there was no installed base with real user data.
 * There is now: a destructive fallback here would silently wipe every saved
 * word in a real user's register on upgrade. The fallback stays registered
 * as a backstop for any *other* future path, but this specific transition
 * must go through the migration.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE lookups ADD COLUMN synonymsNative TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [LookupEntity::class], version = 4, exportSchema = true)
abstract class LookupDatabase : RoomDatabase() {
    abstract fun lookupDao(): LookupDao

    companion object {
        @Volatile
        private var instance: LookupDatabase? = null

        fun get(context: Context): LookupDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LookupDatabase::class.java, "lookups.db")
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

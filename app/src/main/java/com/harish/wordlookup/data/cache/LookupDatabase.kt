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
 * the target language, not just English). version 5 adds six SM-2-lite
 * scheduling columns (round 8 - the register's quiz).
 *
 * [MIGRATION_3_4] was the first real Migration in this app's history - prior
 * bumps relied solely on [fallbackToDestructiveMigration], which was an
 * honest tradeoff while there was no installed base with real user data.
 * [MIGRATION_4_5] follows the same discipline: a destructive fallback on
 * this bump would silently wipe every saved word in a real user's register
 * on upgrade, exactly as it would have on the 3->4 bump. The fallback stays
 * registered as a backstop for any *other* future path, but both of these
 * specific transitions must go through their migrations.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE lookups ADD COLUMN synonymsNative TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE lookups ADD COLUMN dueAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lookups ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lookups ADD COLUMN ease REAL NOT NULL DEFAULT 2.5")
        db.execSQL("ALTER TABLE lookups ADD COLUMN reps INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lookups ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lookups ADD COLUMN lastReviewedAt INTEGER NOT NULL DEFAULT 0")
        // Without this, every word saved before round 8 has dueAt = 0 and
        // would sort as "overdue since the epoch" ahead of everything else
        // forever, rather than entering the queue in the order they were
        // actually saved - createdAt is the closest honest stand-in for
        // "when this word should first come up."
        db.execSQL("UPDATE lookups SET dueAt = createdAt")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lookups_dueAt ON lookups(dueAt)")
    }
}

@Database(entities = [LookupEntity::class], version = 5, exportSchema = true)
abstract class LookupDatabase : RoomDatabase() {
    abstract fun lookupDao(): LookupDao

    companion object {
        @Volatile
        private var instance: LookupDatabase? = null

        fun get(context: Context): LookupDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, LookupDatabase::class.java, "lookups.db")
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

package com.sanjana.duplicatefinder.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {MediaEntity.class},
        version = 2,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract MediaDao mediaDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * Phase 4.2 database migration:
     * Adds the videoFingerprint column without deleting existing data.
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE media " +
                            "ADD COLUMN videoFingerprint TEXT NOT NULL DEFAULT ''"
            );
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "duplicate_finder.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}
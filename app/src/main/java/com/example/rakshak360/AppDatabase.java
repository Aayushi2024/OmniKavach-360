package com.example.rakshak360;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// This tells Room which entities (tables) exist in this databases
@Database(entities = {CrimeEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // Connects your DAO
    public abstract CrimeDao crimeDao();

    // Creates a Singleton (so we don't open multiple connections at once)
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "crime_database")
                            .createFromAsset("databases/crime_data.db") // Loads your file!
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
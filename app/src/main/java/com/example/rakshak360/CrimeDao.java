package com.example.rakshak360;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Insert;
import java.util.List;

@Dao
public interface CrimeDao {

    // This fetches all 3,000 crimes at once
    @Query("SELECT * FROM crimes")
    List<CrimeEntity> getAllCrimes();

    // This is for future use: fetching only nearby crimes to save memory
    @Query("SELECT * FROM crimes WHERE Latitude BETWEEN :minLat AND :maxLat AND Longitude BETWEEN :minLng AND :maxLng")
    List<CrimeEntity> getVisibleCrimes(double minLat, double maxLat, double minLng, double maxLng);

    // This is for when a user reports a new incident in your app
    @Insert
    void insertCrime(CrimeEntity crime);
}
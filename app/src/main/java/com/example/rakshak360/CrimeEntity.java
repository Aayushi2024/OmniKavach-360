package com.example.rakshak360;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "crimes")
public class CrimeEntity {

    // This is the unique ID for every row
    @PrimaryKey
    public int Crime_ID;

    // These must exactly match the columns in your databases
    public String Date;
    public String Area;
    public double Latitude;
    public double Longitude;
    public String Police_Station;
    public String Crime_Type;
    public int Severity;
    public String Status;
}
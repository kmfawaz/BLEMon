package edu.umich.eecs.rtcl.blemon.location;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import edu.umich.eecs.rtcl.blemon.location.LocationContract.PlaceEntry;

public class PlaceReaderDbHelper extends SQLiteOpenHelper {

    //private static final String TEXT_TYPE = " TEXT";
    private static final String INT_TYPE = " INTEGER";
    private static final String REAL_TYPE = " REAL";
    private static final String COMMA_SEP = ",";

    private static final String SQL_CREATE_PLACES =
            "CREATE TABLE " + PlaceEntry.TABLE_NAME + " (" +
                    PlaceEntry.COLUMN_NAME_PLACE_ID + " INTEGER PRIMARY KEY," +
                    PlaceEntry.COLUMN_NAME_LAT + REAL_TYPE + COMMA_SEP +
                    PlaceEntry.COLUMN_NAME_LON + REAL_TYPE + COMMA_SEP +
                    PlaceEntry.COLUMN_NAME_COUNT + INT_TYPE +
                    " )";



    private static final String SQL_DELETE_PLACES =
            "DROP TABLE IF EXISTS " + PlaceEntry.TABLE_NAME;



    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 11;
    public static final String DATABASE_NAME = "PlaceReader.db";


    public PlaceReaderDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_PLACES);
        db.execSQL( "Create Index PlaceTable_latitude_idx ON " + PlaceEntry.TABLE_NAME+"("+PlaceEntry.COLUMN_NAME_LAT+
                "," + PlaceEntry.COLUMN_NAME_LON+");");
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_PLACES);
        onCreate(db);
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}


package edu.umich.eecs.rtcl.blemon.location;

import java.util.LinkedList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PointF;
import android.location.Location;

import edu.umich.eecs.rtcl.blemon.location.LocationContract.PlaceEntry;

public class LocationAnonymizer {

    //maps a location to anonymous visited place
    //test on an existing trace

    //basic data structure
    //map id to location
    LinkedList <placeRecord> locationDB;
    int currentID;
    final double RADIUS = 75;
    PlaceReaderDbHelper mDbHelper;

    long lastUpdatedTime = 0;
    int currentPlace = -1;
    public LocationAnonymizer (Context context) {
        currentID = 0;
        mDbHelper = new PlaceReaderDbHelper(context);
    }

    public int getPlace (Location location) {
        //iterate overall records in DB
        //if matching --> fine
        //o.w. --> add new one

        if (location.getAccuracy()>200) {
            currentPlace = -1;
            return -1;
        }

        if (location.getTime()==lastUpdatedTime) {
            //System.out.println("returned from cache");
            return currentPlace; // same location sample, no need to update location or places
        }
        lastUpdatedTime = location.getTime();
        //if same location sample, return old place; need a cached test based on time/

        placeRecord record = getPlaceFromDb (location);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        if (record == null){
            //insert new element and return new place id
            ContentValues values = new ContentValues();
            values.put(PlaceEntry.COLUMN_NAME_LAT, location.getLatitude());
            values.put(PlaceEntry.COLUMN_NAME_LON, location.getLongitude());
            values.put(PlaceEntry.COLUMN_NAME_COUNT, 1);
            long newRowId;
            newRowId = db.insert(PlaceEntry.TABLE_NAME, null, values);
            currentPlace = (int)newRowId;
            return currentPlace;
        }

        //o.w. return existing ID after updating record
        record.updateRecord(location);

        ContentValues values = new ContentValues();
        values.put(PlaceEntry.COLUMN_NAME_LAT, record.latitude);
        values.put(PlaceEntry.COLUMN_NAME_LON, record.longitude);
        values.put(PlaceEntry.COLUMN_NAME_COUNT, record.count);

        String selection = PlaceEntry.COLUMN_NAME_PLACE_ID + " = " + record.placeID;
        db.update(PlaceEntry.TABLE_NAME, values, selection, null);
        currentPlace = record.placeID;
        return currentPlace;
    }


    //get records within 100m of input location

    public void printDB () {
        for (placeRecord record:locationDB) {
            //System.out.println("LocDBB-"+record.toString());
        }
    }

    private placeRecord getPlaceFromDb (Location location) {

        PointF center = new PointF((float)location.getLatitude(), (float)location.getLongitude());
        PointF p1 = calculateDerivedPosition(center, RADIUS, 0);
        PointF p2 = calculateDerivedPosition(center, RADIUS, 90);
        PointF p3 = calculateDerivedPosition(center, RADIUS, 180);
        PointF p4 = calculateDerivedPosition(center, RADIUS, 270);
        //System.out.println(location.getLatitude()+","+location.getLongitude());
        //System.out.println(p1.x+","+p1.y);
        //System.out.println(p2.x+","+p2.y);
        //System.out.println(p3.x+","+p3.y);
        //System.out.println(p4.x+","+p4.y);


        //select statement or cursor?

        String strWhere =  ""
                + PlaceEntry.COLUMN_NAME_LAT + " > " + String.valueOf(p3.x) + " AND "
                + PlaceEntry.COLUMN_NAME_LAT + " < " + String.valueOf(p1.x) + " AND "
                + PlaceEntry.COLUMN_NAME_LON + " < " + String.valueOf(p2.y) + " AND "
                + PlaceEntry.COLUMN_NAME_LON + " > " + String.valueOf(p4.y);

        //System.out.println(strWhere);
        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        //columns we are interested in
        String[] projection = {
                PlaceEntry.COLUMN_NAME_PLACE_ID,
                PlaceEntry.COLUMN_NAME_LAT,
                PlaceEntry.COLUMN_NAME_LON,
                PlaceEntry.COLUMN_NAME_COUNT
        };

        String sortOrder = PlaceEntry.COLUMN_NAME_COUNT + " DESC"; //favor clusters with higher support over ones with less
        String limit = "1"; //favor older clusters over new ones

        Cursor c = db.query(
                PlaceEntry.TABLE_NAME,  // The table to query
                projection,                               // The columns to return
                strWhere,                                // The columns for the WHERE clause
                null,                                    // The values for the WHERE clause
                null,                                     // don't group the rows
                null,                                     // don't filter by row groups
                sortOrder,                                // The sort order
                limit                                     //limit string
        );
        c.moveToFirst();
        // no matching record
        if (c.getCount()==0) {
            return null;
        }
        // return matching record
        int placeID = c.getInt(c.getColumnIndex(PlaceEntry.COLUMN_NAME_PLACE_ID));
        double latitude = c.getDouble(c.getColumnIndex(PlaceEntry.COLUMN_NAME_LAT));
        double longitude = c.getDouble(c.getColumnIndex(PlaceEntry.COLUMN_NAME_LON));
        int count = c.getInt(c.getColumnIndex(PlaceEntry.COLUMN_NAME_COUNT));
        placeRecord record = new placeRecord(latitude,longitude,count,placeID);
        c.close();
        return record;
    }

    private PointF calculateDerivedPosition(PointF point, double range, double bearing)
    {
        double EarthRadius = 6371000; // m

        double latA = Math.toRadians(point.x);
        double lonA = Math.toRadians(point.y);
        double angularDistance = range / EarthRadius;
        double trueCourse = Math.toRadians(bearing);

        double lat = Math.asin(
                Math.sin(latA) * Math.cos(angularDistance) +
                        Math.cos(latA) * Math.sin(angularDistance)
                                * Math.cos(trueCourse));

        double dlon = Math.atan2(
                Math.sin(trueCourse) * Math.sin(angularDistance)
                        * Math.cos(latA),
                Math.cos(angularDistance) - Math.sin(latA) * Math.sin(lat));

        double lon = ((lonA + dlon + Math.PI) % (Math.PI * 2)) - Math.PI;

        lat = Math.toDegrees(lat);
        lon = Math.toDegrees(lon);

        PointF newPoint = new PointF((float) lat, (float) lon);

        return newPoint;

    }

    class placeRecord {
        private double latitude;
        private double longitude;
        private int count;
        private int placeID;

        public placeRecord(double latitude,	 double longitude, int count, int placeID) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.count = count;
            this.placeID = placeID;
        }

        public void updateRecord(Location loc) {
            count ++;
            latitude = (latitude*(count-1) + loc.getLatitude())/count;
            longitude = (longitude*(count-1) + loc.getLongitude())/count;
        }

        public String toString() {
            return placeID+":"+latitude+":"+longitude+":"+count;
        }

    }

}

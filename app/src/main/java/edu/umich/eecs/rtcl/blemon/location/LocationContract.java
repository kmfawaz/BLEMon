package edu.umich.eecs.rtcl.blemon.location;

/**
 * Created by fawaz on 10/7/2015.
 */
import android.provider.BaseColumns;

public final class LocationContract {

    public LocationContract() {}

    /* Inner class that defines the table contents */
    public static abstract class PlaceEntry implements BaseColumns {
        public static final String TABLE_NAME = "place";
        public static final String COLUMN_NAME_PLACE_ID = "placeid";
        public static final String COLUMN_NAME_LAT = "latitude";
        public static final String COLUMN_NAME_LON = "longitude";
        public static final String COLUMN_NAME_COUNT = "count";
    }

}
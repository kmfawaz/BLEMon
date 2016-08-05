package edu.umich.eecs.rtcl.blemon;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by fawaz on 10/7/2015.
 */
public class LocationSettings {
    // read location settings
    // write location settings
    final private static String LOCATION_ACCESS_SETTING_KEY = "LOCATION_ACCESS_SETTING_KEY";
    final private static String SETTINGS_FILE = "LOCATION_ACCESS_SETTINGS_FILE";


    public static void writeLocationSetting (Context context,boolean locationValue) {
        ScannerService.locationSetting = locationValue;
        SharedPreferences sharedPref = context.getSharedPreferences(SETTINGS_FILE,Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(LOCATION_ACCESS_SETTING_KEY, locationValue);
        editor.commit();
        ////System.out.println("must have received event; toggle button checked?\t"+locationValue);

    }

    public static boolean readLocationSetting (Context context) {

        SharedPreferences sharedPref = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE);
        boolean locationValue = sharedPref.getBoolean(LOCATION_ACCESS_SETTING_KEY, true);
        ScannerService.locationSetting = locationValue;
        return locationValue;
    }


    /**
     * Helper class for Bluetooth LE utils.
     *
     * @hide
     */
    public static class Utils {

        /**
         * Returns a string composed from a {@link SparseArray}.
         */
        static String toString(SparseArray<byte[]> array) {
            if (array == null) {
                return "null";
            }
            if (array.size() == 0) {
                return "{}";
            }
            StringBuilder buffer = new StringBuilder();
            buffer.append('{');
            for (int i = 0; i < array.size(); ++i) {
                buffer.append(array.keyAt(i)).append("=").append(array.valueAt(i));
            }
            buffer.append('}');
            return buffer.toString();
        }

        /**
         * Returns a string composed from a {@link Map}.
         */
        static <T> String toString(Map<T, byte[]> map) {
            if (map == null) {
                return "null";
            }
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder buffer = new StringBuilder();
            buffer.append('{');
            Iterator<Map.Entry<T, byte[]>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<T, byte[]> entry = it.next();
                Object key = entry.getKey();
                buffer.append(key).append("=").append(Arrays.toString(map.get(key)));
                if (it.hasNext()) {
                    buffer.append(", ");
                }
            }
            buffer.append('}');
            return buffer.toString();
        }

        public static String hashString (String input)  {
            InputStream in = new ByteArrayInputStream(input.getBytes());
            MessageDigest digester;
            try {
                digester = MessageDigest.getInstance("SHA-256");
                byte[] bytes = new byte[8192];
                int byteCount;
                while ((byteCount = in.read(bytes)) > 0) {
                    digester.update(bytes, 0, byteCount);
                }
                byte[] digest = digester.digest();
                return bytesToHex (digest);
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "HASH_FAILED"; // for some reason we weren't able to hash the address
        }

        final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
        public static String bytesToHex(byte[] bytes) {
            char[] hexChars = new char[bytes.length * 2];
            for ( int j = 0; j < bytes.length; j++ ) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }


    }
}

package edu.umich.eecs.rtcl.blemon;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import edu.umich.eecs.rtcl.blemon.location.LocationAnonymizer;

//let's put ble scanning logic here
//we might have to account for two platforms; let's first implement for the old one
//what happens when we don't google play services installed?
public class ScannerService extends Service  implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {



    private static final int NOTIFICATION = 1;
    public static final String INTENT_FILTER = "edu.umich.eecs.rtcl.bt_scanner.notif";
    public static final String INTENT_STOP_SERVICE = "edu.umich.eecs.rtcl.bt_scanner.notif.stop_service";
    public static final String INTENT_START_SCAN = "edu.umich.eecs.rtcl.bt_scanner.start_scan";
    public static final String INTENT_GO_TO_ACTIVITY = "edu.umich.eecs.rtcl.bt_scanner.notif.go_to_activity";

    public static final String action_exit = "Stop Data Collection";
    public static final String service_connected = "bluetooth scanning service running";

    public static final long SCAN_PERIOD = 60*1000; //60 seconds
    public static final long ALARM_INTERVAL = SCAN_PERIOD+1000; // factor determines duty cycle
    public static final long COUNTER_TO_UPLOAD = 20; //upload each counter*factor*scan_period

    public static final String ALARM_ACTION = "GET_TIMER_EVENT54";





    ////file upload:
    private BufferedWriter writer;


    private String currentFileName = System.currentTimeMillis()+extension;
    private final static String folder = "ble_log";
    private final static String extension = "-adv";
    public static String DEV_ID = ""; // uniquely identify each device on the server side


    volatile static Boolean systemFlag = false;
    //////////////////////////


    private NotificationManager mNotificationManager = null;
    private final NotificationCompat.Builder mNotificationBuilder = new NotificationCompat.Builder(this);


    BluetoothAdapter mBluetoothAdapter;

    public static boolean locationSetting = true; //used to decide whether to access location or not

    final public static String LOG_TAG = "MYBLE";

    GoogleApiClient mGoogleApiClient;
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    /*
    double longitude = -1;
    double latitude = -1;
    double accuracy = -1; // meters
    */
    long timeSinceLastUpdate = -1; //seconds
    long reportedTime = 0; //timestamp of the latest location update we got
    int currentPlace = -1;

    private Handler mHandler;

    public ScannerService() {
        mBluetoothAdapter =  BluetoothAdapter.getDefaultAdapter();
    }

    LocationAnonymizer locAnon;

    @Override
    public void onCreate() {
        super.onCreate();
        //System.out.println("creating service");
        DEV_ID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);; // uniquely identify each device on the server side

        locAnon = new LocationAnonymizer(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
        }

        long startedTime = 0; // clear the seen list every minute or two?
        //good balance between recurrence of some events and a very long log file

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
            //TODO do something useful
        //System.out.println("service started");
        buildGoogleApiClient();
            mGoogleApiClient.connect();
        setupNotifications();
        showNotification();
        locationSetting = LocationSettings.readLocationSetting(this);


        //Toast.makeText(getApplicationContext(), "We have the service up and running", Toast.LENGTH_LONG).show();

        IntentFilter filter = new IntentFilter(INTENT_FILTER);
        registerReceiver(receiver, filter);

        startedTime = System.currentTimeMillis();
        //initialize timer as well
        startScanningAlarm();
        try {
            writer = Util.initialzeWriter(folder, currentFileName, this);
        } catch (IOException e) {}

        return Service.START_STICKY;
    }

    AlarmManager am;
    PendingIntent alarmPendingIntent;

    private void startScanningAlarm () {
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmHandler.class);
        intent.setAction(ALARM_ACTION);
        alarmPendingIntent = PendingIntent.getBroadcast(this, 2, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), ALARM_INTERVAL, alarmPendingIntent);
    }


    @Override
    public void onDestroy(){

        ////System.out.println("service stopped");
        Log.v("MYBLE", "service stopping");
        mGoogleApiClient.disconnect();
        //Toast.makeText(getApplicationContext(), "So, the service was destroyed", Toast.LENGTH_LONG).show();
        unregisterReceiver(receiver);
      //  if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
      //  }
        discoveredOnes.clear();
        closeNotification(); //maybe replace with a notification that says start the activity again.
        am.cancel(alarmPendingIntent);
        super.onDestroy();
    }



    BroadcastReceiver receiver  = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();


            String innerAction = intent.getStringExtra("action");
            if (innerAction==null) {
                //System.out.println("something wrong happened");

                return;
            }
            if (innerAction.equals(ScannerService.INTENT_START_SCAN)) {
                ////System.out.println("got the start scan intent");
                Log.v("MYBLE", "got the start scan intent");
                boolean attemptUpload = intent.getBooleanExtra("attemptUpload", false);
                performScanningLowLevel(attemptUpload);
            }
            if (innerAction.equals(ScannerService.INTENT_STOP_SERVICE)) {
                //System.out.println("got the close intent");
                Log.v("MYBLE", "got the close intent");
                //Toast.makeText(getApplicationContext(), "Received intent to close the service", Toast.LENGTH_LONG).show();
                ScannerService.this.stopSelf();
            }
            else if (innerAction.equals(ScannerService.INTENT_GO_TO_ACTIVITY)) {
                //System.out.println("got the intent to start activity");
                Log.v("MYBLE", "got the start intent");
                //Toast.makeText(getApplicationContext(), "Received intent to start the activity back again", Toast.LENGTH_LONG).show();
                Intent startActIntent = new Intent(getApplicationContext(), BLEMonUI.class); //instruct the activity to launch again
                startActIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ScannerService.this.startActivity(startActIntent);

            }
        }
    };

    //still not sure about these parts
    private void setupNotifications() { //called in onCreate()
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        /*
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, ScannerService.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                0);
        */
        Intent intentClose = new Intent(INTENT_FILTER);
        intentClose.putExtra("action", ScannerService.INTENT_STOP_SERVICE);

        Intent intentActivity = new Intent(INTENT_FILTER);
        intentActivity.putExtra("action", ScannerService.INTENT_GO_TO_ACTIVITY);

        PendingIntent pendingCloseIntent = PendingIntent.getBroadcast(this, 0, intentClose, PendingIntent.FLAG_CANCEL_CURRENT );
        PendingIntent pendingActivityIntent = PendingIntent.getBroadcast(this, 1, intentActivity, PendingIntent.FLAG_CANCEL_CURRENT );

        mNotificationBuilder
                .setSmallIcon(R.mipmap.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(getText(R.string.app_name))
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingActivityIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        action_exit, pendingCloseIntent)
                .setOngoing(true);
    }

    private void showNotification() {
        mNotificationBuilder
                .setTicker(service_connected)
                .setContentText(service_connected);
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION, mNotificationBuilder.build());
        }
    }

    private void closeNotification() {

        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////BLUETOOTH STUFF HERE//////////////////////////////////////////////////

    private HashSet<String> discoveredOnes = new HashSet<String>();
    private static final int ONE_MINUTE_MILLI = 1000*60;
    private static final int FIVE_MINUTES_MILLI = 5*1000*60;


    private boolean mScanning;


    private void performScanningLowLevel (final boolean attemptUpload) {
        if (mBluetoothAdapter==null) {
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
            Log.v("MYBLE", "got command to start scan but adapter is null");
        }

        if (mScanning) {
            Log.v("MYBLE", "already scanning, no need to start new scan");
            return;
        }

        mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);

                Log.v("MYBLE", "stop scan after timer, scanned that much:\t" + adv);
                Log.v("MYBLE", "we scanned for this long in ms:\t" + (System.currentTimeMillis()-startUpload));
                adv = 0;
                if (attemptUpload) {
                    Log.v("MYBLE", "attempting upload after finishing scans");
                    long current = System.currentTimeMillis();
                    try {
                        uploadData (current);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, SCAN_PERIOD);

        mScanning = true;
        startUpload = System.currentTimeMillis();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
        Log.v("MYBLE", "start scan");
        discoveredOnes = new HashSet<String>(); //whenever scan starts
        Log.v("MYBLE","start scanning");
    }

    int adv  =0;
    long startUpload = 0;
    BluetoothAdapter.LeScanCallback mLeScanCallback  = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device,int rssi, byte[] scanRecord) {
            //Log.v("MYBLE", "received an advertisement");
            if (device == null) {
                return;
            }
            adv++;
            long currentTime = System.currentTimeMillis();
            long timeDiff = (currentTime - startedTime)/FIVE_MINUTES_MILLI; //in minutes
            if (timeDiff>0) {
                discoveredOnes.clear();
                startedTime = System.currentTimeMillis();
            }

           // if (!discoveredOnes.contains(device.getAddress())) {

                populateLocation();

                BluetoothClass devClass =device.getBluetoothClass();
                int devType = -1;
                if (devClass!=null) {
                    devType = devClass.getDeviceClass();
                }

                String hashedAddress = LocationSettings.Utils.hashString(device.getAddress()); //at some point we were not hashing addresses before hand
                String record = System.currentTimeMillis() + "," + device.getName() + "," + hashedAddress + "," + device.getType() + "," + rssi + "," + devType+","+ LocationSettings.Utils.bytesToHex(scanRecord);
                record = record + "," + device.getBondState();

                String locRecord = currentPlace+","+timeSinceLastUpdate;
                record = record + ","+locRecord; //adding the location to the mix
                ////System.out.println(record);
                appendToLogFile(record);

          //  }

            discoveredOnes.add(device.getAddress());
        }
    };



//we might decide what to do with this file; it has to be kept private anyways
    private void appendToLogFile(String record) {
        try
        {
            writer.write(record + "\r\n");
            writer.flush();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }


    @Override
    public void onConnected(Bundle connectionHint) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            /*
            latitude = mLastLocation.getLatitude();
            longitude = mLastLocation.getLongitude();
            accuracy = mLastLocation.getAccuracy();
            */
            currentPlace = locAnon.getPlace(mLastLocation);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // do nothing
    }

    //we have to put the location switch here or somewhere
    private void populateLocation() {
        if (!mGoogleApiClient.isConnected()) {
            return;
        }

        //if we have location permission from user access location
        //otherwise just report old location but which updating time since last location update if possible
        if (locationSetting) {
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

            if (mLastLocation != null) {
                currentPlace = locAnon.getPlace(mLastLocation);
                reportedTime = mLastLocation.getTime();
            }
        }

        long currentTime = System.currentTimeMillis();
        timeSinceLastUpdate =  Math.abs(reportedTime-currentTime)/1000; //time difference in seconds
        //System.out.println(reportedTime+","+currentTime+","+timeSinceLastUpdate);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private void uploadData (long current) throws IOException {
        //do nothing if wifi is not connected.
        if (!Util.isWiFiConnected(this)) {
            Log.v("MYBLE","wifi not connected");
            return;
        }
        writer.close();
        currentFileName = ""+ current+extension;
        //we check for all non-uplaoded files every time, but atmost one an hour
        writer = Util.initialzeWriter (folder, currentFileName,this); //start writing to a new file
        Log.v("MYBLE","new file under the name\t"+currentFileName);
        File mydir = getDir(folder, Context.MODE_PRIVATE); //Creating an internal directory

        File [] logFiles = mydir.listFiles();
        LinkedList <File> filesToUpload = new LinkedList <File>();
        for (int i=0;i<logFiles.length;i++) {
            File oneFile = logFiles[i];
            //upload all the files except for the current one
            if (!oneFile.getName().equals(currentFileName)) {
                Log.v("MYBLE","iterating over files:\t"+current + "::::::"+ oneFile.getName()+"::::"+oneFile.lastModified());
                filesToUpload.add(oneFile);
            }
        }

        //another async task is running, don't do anything for now
        synchronized(systemFlag) {
            if (systemFlag) {
                return;
            }
            Log.v("MYBLE",systemFlag+"");
            //upload all files using an async task
            systemFlag = true;
        }

        new Util.FileUploader().execute(filesToUpload.toArray(new File [1]));
    }
}

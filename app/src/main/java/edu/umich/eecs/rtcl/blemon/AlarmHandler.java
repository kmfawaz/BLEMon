package edu.umich.eecs.rtcl.blemon;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;


//more or less like a state machine
//scan for wireless, if one matches a store, turn on bluetooth, and scan with a finer frequency
public class AlarmHandler extends BroadcastReceiver {

    static int counter = 0 ; //counts alarm iterations
    static long lastTime = 0 ; //counts alarm iterations

    //alarm has been fired
    @Override
    public void onReceive(Context context, Intent intent) {
        //System.out.println(intent);
        if (intent.getAction()!=null) {
            //System.out.println(intent.getAction());

            if (intent.getAction().equals(ScannerService.ALARM_ACTION)) {
                long currentTime = System.currentTimeMillis();
                Log.v("MYBLE","time since last timer: \t"+(currentTime-lastTime));
                handleTimer(context);
                lastTime=System.currentTimeMillis();
            }
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                handleBLState (state,context);
            }
        }

    }

    private void handleTimer(Context context) {

        //five minutes, attempt upload
        boolean attemptUpload = false;
        if (counter%ScannerService.COUNTER_TO_UPLOAD==0) {
            attemptUpload = true;
        }
        Log.v("MYBLE","alarm fired with counter:\t"+counter);
        counter ++;
        Intent intent = new Intent(ScannerService.INTENT_FILTER);
        // add data
        intent.putExtra("action", ScannerService.INTENT_START_SCAN);
        intent.putExtra("attemptUpload", attemptUpload);
        context.sendBroadcast(intent);
        //notify scanning service to do something -- scan for some period of time


    }


    private void handleBLState (int state, Context context) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                Log.v("MYBLE","bluetooth disabled!");
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                //do something perhaps
                break;
            case BluetoothAdapter.STATE_ON:
                Log.v("MYBLE", "bluetooth enabled!");
                //do something perhaps
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
                break;
        }
    }

}
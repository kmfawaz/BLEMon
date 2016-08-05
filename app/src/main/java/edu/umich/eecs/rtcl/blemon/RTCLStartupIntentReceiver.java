package edu.umich.eecs.rtcl.blemon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

//signals that the device completed boot, so we can start the service
//actually we have to start all services !!!!!!!!!!!!!!!!!!!!!!
public class RTCLStartupIntentReceiver extends BroadcastReceiver {

    //start all services here
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {

            Intent serviceIntent = new Intent(context, ScannerService.class);
            //String message = "start service...";
            //intent.putExtra(RTCLMonUI.EXTRA_MESSAGE, message);
            context.startService(serviceIntent); // this will start the alarm as well
            Log.v("MYBLE","booting the app launcher");

        }
    }

}
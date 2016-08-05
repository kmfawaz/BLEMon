package edu.umich.eecs.rtcl.blemon;

import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

//do we need to connect to gms from here?
public class BLEMonUI extends Activity {

    Button start;
    Button stop;
    WebView webview;
    ToggleButton locationSwitch;

    final static String EXTRA_MESSAGE = "edu.umich.eecs.rtcl.START_SERVICE";
    public static final int REQUEST_ENABLE_BT = 1023;

    BluetoothAdapter mBluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blemon_ui);

        start = (Button)findViewById(R.id.start);
        start.setOnClickListener(startListener);

        stop = (Button)findViewById(R.id.stop);
        stop.setOnClickListener(stopListener);

        webview = (WebView)findViewById(R.id.webview);
        webview.loadUrl("file:///android_asset/policy.html");

        locationSwitch = (ToggleButton)findViewById(R.id.locationSwitch);
        initializeLocationSwitch();
        locationSwitch.setOnCheckedChangeListener(locationSwitchListener);

        addEmailLink();
        mBluetoothAdapter =  BluetoothAdapter.getDefaultAdapter();
        checkBLEFeature();

        enableBluetooth();

    }

    void checkBLEFeature() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    void enableBluetooth () {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    public void addEmailLink() {
        TextView privacyPolicy= (TextView)findViewById(R.id.textView2);
        String mystring=new String("rtclmon-conf@umich.edu");
        SpannableString content = new SpannableString(mystring);
        content.setSpan(new UnderlineSpan(), 0, mystring.length(), 0);
        privacyPolicy.setText(content);
        privacyPolicy.setClickable(true);
        privacyPolicy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("mailto:rtclmon-conf@umich.edu"));

                startActivity(Intent.createChooser(intent, "Send Email"));
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private OnClickListener startListener = new OnClickListener() {
        public void onClick(View v) {
            startScannerService();//start the location activity
        }
    };

    private OnClickListener stopListener = new OnClickListener() {
        public void onClick(View v) {
            stopScannerService();//stop the location activity
        }
    };


    private void initializeLocationSwitch() {
        boolean switchValue = LocationSettings.readLocationSetting(this);
        locationSwitch.setChecked(switchValue);
    }

    private CompoundButton.OnCheckedChangeListener locationSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            LocationSettings.writeLocationSetting(BLEMonUI.this,isChecked);
            //update value from here
        }

    };

    void startScannerService () {
        Intent intent= new Intent(this, ScannerService.class);
        // potentially add data to the intent, are we going to need it?
        String message = "start service...";
        intent.putExtra(EXTRA_MESSAGE, message);

        startService(intent);
    }

    void stopScannerService() {

        Intent intent= new Intent(this, ScannerService.class);
        // potentially add data to the intent, are we going to need it?
        String message = "start service...";
        intent.putExtra(EXTRA_MESSAGE, message);

        stopService(intent);
    }






}

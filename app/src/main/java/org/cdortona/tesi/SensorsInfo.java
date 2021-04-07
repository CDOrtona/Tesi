package org.cdortona.tesi;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Cristian D'Ortona
 *
 * TESI DI LAUREA IN INGEGNERIA ELETTRONICA E DELLE TELECOMUNICAZIONI
 *
 */


public class SensorsInfo extends AppCompatActivity {

    private final String TAG = "SensorInfo";

    //I initialize an object from the class ConnectToGattServer which handles the connection to the GATT server of the ESP32
    ConnectToGattServer connectToGattServer;

    //GATT
    String deviceAddress;
    String deviceName;
    boolean connectedToGatt = false;
    private String stateConnection = null;

    //flags used to tell which characteristic has changed
    boolean tempChanged = false;
    boolean heartChanged = false;

    TextView addressInfo;
    TextView nameInfo;
    TextView connectionState;
    TextView tempValue;
    TextView heartValue;
    TextView brightnessValue;
    TextView positionValue;

    //location
    FusedLocationProviderClient locationProviderClient;
    ArrayList<Location> locationList;

    //Toolbar
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors_info);

        //UI setup
        addressInfo = findViewById(R.id.address_textView);
        nameInfo = findViewById(R.id.name_textView);
        connectionState = findViewById(R.id.connection_state_textView);
        tempValue = findViewById(R.id.textView_temp);
        heartValue = findViewById(R.id.textView_heart);
        brightnessValue = findViewById(R.id.textView_brightness);
        positionValue = findViewById(R.id.textView_position);

        //I have to retrieve the info from the Intent which called this activity
        Intent receivedIntent = getIntent();
        deviceAddress = receivedIntent.getStringExtra(StaticResources.EXTRA_CHOOSEN_ADDRESS);
        deviceName = receivedIntent.getStringExtra(StaticResources.EXTRA_CHOOSEN_NAME);


        //Setting the values of the TextViews objects
        addressInfo.setTypeface(Typeface.SANS_SERIF);
        addressInfo.setText(deviceAddress);
        nameInfo.setTypeface(Typeface.SANS_SERIF);
        nameInfo.setText(deviceName);
        //hard coded, I must change it later
        connectionState.setTextColor(Color.RED);
        connectionState.setText("Disconnected");

        //calling the constructor in order to build a BluetoothAdaptor object
        connectToGattServer = new ConnectToGattServer(deviceAddress, this);

        //location
        locationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationList = new ArrayList<>();

        //here I'm specifying the intent filters I want to subscribe to in order to get their updates
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(StaticResources.ACTION_CONNECTION_STATE);
        intentFilter.addAction(StaticResources.ACTION_CHARACTERISTIC_CHANGED_READ);
        registerReceiver(bleBroadcastReceiver, intentFilter);

        //Toolbar
        toolbar = findViewById(R.id.toolbar_sensors);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

    }

    @Override
    protected void onResume() {
        super.onResume();
        locationUpdate();
    }

    //Toolbar set up
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.toolbar_menu_sensors, menu);
        return true;
    }

    //this is called whenever the user clicks on the back arrow
    //it works as an UP button
    public boolean onSupportNavigateUp() {
        //this is called when the activity detects the user pressed the back button
        onBackPressed();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case (R.id.action_connect):
                connectToGatt();
                return true;
            case (R.id.action_disconnect):
                invalidateOptionsMenu();
                disconnectFromGatt();
                return true;
            case (R.id.action_graph_rssi):
                Intent rssiGraph = new Intent(SensorsInfo.this, GraphRssi.class);
                rssiGraph.putExtra(StaticResources.EXTRA_CHOOSEN_ADDRESS, deviceAddress);
                startActivity(rssiGraph);
                return true;
            case (R.id.action_mqtt):
                Toast.makeText(this, "Send data to Cloud", Toast.LENGTH_SHORT).show();
                //MQTT
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (connectedToGatt) {
            menu.findItem(R.id.action_connect).setVisible(false);
            menu.findItem(R.id.action_disconnect).setVisible(true);
            menu.findItem(R.id.action_mqtt).setEnabled(true);
            accessLocation();
            return true;
        } else if (!connectedToGatt) {
            menu.findItem(R.id.action_connect).setVisible(true);
            menu.findItem(R.id.action_disconnect).setVisible(false);
            menu.findItem(R.id.action_mqtt).setEnabled(false);
            return true;
        } else {
            return super.onPrepareOptionsMenu(menu);
        }
    }

    //I override this method to make sure that the GATT server is disconnected if the users goes back to the previous activity
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        disconnectFromGatt();
    }

    //this is used to receive the broadcast announcements that are being sent from the class ConnectToGattServer()
    //the received broadcasters depend on the intent filters declared above
    final BroadcastReceiver bleBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String broadcastReceived = intent.getAction();
            Log.d("bleBroadCastReceiver", "The received Broadcast is: " + broadcastReceived);

            switch (broadcastReceived) {

                case StaticResources.ACTION_CONNECTION_STATE:
                    stateConnection = intent.getStringExtra(StaticResources.EXTRA_STATE_CONNECTION);
                    if (stateConnection.equals(StaticResources.STATE_CONNECTED)) {
                        connectedToGatt = true;
                        invalidateOptionsMenu();
                        connectionStateString(StaticResources.STATE_CONNECTED);
                    } else if (stateConnection.equals(StaticResources.STATE_DISCONNECTED)) {
                        connectedToGatt = false;
                        invalidateOptionsMenu();
                        connectionStateString(StaticResources.STATE_DISCONNECTED);
                    }
                    break;
                //this received broadcast lets the activity that subbed to this intent filter know which is the characteristic that has changed
                case StaticResources.ACTION_CHARACTERISTIC_CHANGED_READ:
                    /*String notifiedCharacteristic = intent.getStringExtra(StaticResources.EXTRA_CHARACTERISTIC_NOTIFIED);
                    Log.d(TAG, "Characteristic notified: " + notifiedCharacteristic);
                    if (notifiedCharacteristic.equals(StaticResources.ESP32_TEMP_CHARACTERISTIC)) {
                        Log.d(TAG, "Characteristic is Temp");
                        tempChanged = true;
                        heartChanged = false;
                    } else if (notifiedCharacteristic.equals(StaticResources.ESP32_HEARTH_CHARACTERISTIC)) {
                        tempChanged = false;
                        heartChanged = true;
                        Log.d(TAG, "Characteristic is Heart");
                    }
                    break;*/
                    Log.d("whichCharChanged", StaticResources.EXTRA_CHARACTERISTIC_CHANGED);
                    switch (intent.getStringExtra(StaticResources.EXTRA_CHARACTERISTIC_CHANGED)){
                        case StaticResources.ESP32_TEMP_CHARACTERISTIC:
                            tempValue.setText(intent.getStringExtra(StaticResources.EXTRA_TEMP_VALUE));
                            break;
                        case StaticResources.ESP32_HEARTH_CHARACTERISTIC:
                            heartValue.setText(intent.getStringExtra(StaticResources.EXTRA_HEART_VALUE));
                            break;
                        case  StaticResources.ESP32_BRIGHTNESS_CHARACTERISTIC:
                            brightnessValue.setText(intent.getStringExtra(StaticResources.EXTRA_BRIGHTNESS_VALUE));
                            break;
                    }
                    /*String onUpdateTempValue = intent.getStringExtra(StaticResources.EXTRA_TEMP_VALUE);
                    String onUpdateHeartValue = intent.getStringExtra(StaticResources.EXTRA_HEART_VALUE);
                    String onUpdateBrightnessValue = intent.getStringExtra(StaticResources.EXTRA_BRIGHTNESS_VALUE);
                    tempValue.setText(onUpdateTempValue);
                    heartValue.setText(onUpdateHeartValue);
                    brightnessValue.setText(onUpdateBrightnessValue);*/
            }

        }
    };

    public void locationUpdate() {
        //this creates a location request with default parameters
        LocationRequest locationRequest = LocationRequest.create();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationProviderClient.requestLocationUpdates(locationRequest, locationCallBack, Looper.getMainLooper());
    }

    private final LocationCallback locationCallBack = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            //this is similar to a for each loop that stores all the locations found in the location arrayList
            locationList.addAll(locationResult.getLocations());
        }
    };

    private void accessLocation(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            Log.d(TAG, "Location permission disabled, sent request permission activation dialog");
            accessLocation();
        }
        else {
            Log.d(TAG, "Location permission enabled");
            locationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    //location is null if there is no known location found
                    String position = "Lo: " + location.getLongitude() + '\n' + '\n'
                            + "La: " + location.getLatitude();
                    positionValue.setText(position);
                }
            });
        }
    }

    //add an if that checks if the adaptor is connected to the GATT server already
    public void connectToGatt() {
        connectToGattServer.connectToGatt(deviceAddress);
        //this makes sure that there is a time out error if it takes more than 10 seconds to connect to the remote peripheral
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if(stateConnection == null){
                    Log.w("connectToGatt", "The connection has timed out");
                    Toast.makeText(getApplicationContext(), "Timeout connection, try again", Toast.LENGTH_SHORT).show();
                    connectionStateString(StaticResources.STATE_DISCONNECTED);
                }
            }
        }, 10000);
        connectionStateString(StaticResources.STATE_CONNECTING);
    }

    public void disconnectFromGatt() {
        stateConnection = null;
        connectToGattServer.disconnectGattServer();
        connectedToGatt = false;
        connectionStateString(StaticResources.STATE_DISCONNECTED);
        //this is gonna flush the location stored in the location variable
        locationProviderClient.flushLocations();
        locationProviderClient.removeLocationUpdates(locationCallBack);
    }

    //this dynamically changes the string color and text of the string that shows on screen the connection state
    private void connectionStateString(String state){
        switch (state) {
            case StaticResources.STATE_CONNECTED:
                connectionState.setTextColor(Color.GREEN);
                connectionState.setText(StaticResources.STATE_CONNECTED);
                break;
            case StaticResources.STATE_CONNECTING:
                connectionState.setTextColor(Color.YELLOW);
                connectionState.setText(StaticResources.STATE_CONNECTING);
                break;
            case StaticResources.STATE_DISCONNECTED:
                connectionState.setTextColor(Color.RED);
                connectionState.setText(StaticResources.STATE_DISCONNECTED);
                break;
        }
    }
}

package com.example.SPLICEApp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BleScanner extends AppCompatActivity {
    private static HashMap<String, String> db = new HashMap<>();
    private String DEVICE_ADDRESS_FILTER = "XX:XX:XX:XX:XX:XX";
    private HashSet<String> stored_addresses = new HashSet<>();
    private HashMap<View, byte[]> trigger_by_address = new HashMap<>();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_scanner);
        TextView tv = (TextView) findViewById(R.id.NumDevices);
        tv.setText("Devices Found: " + db.size());

        // populate UI with stored database
        TableLayout tl = (TableLayout) findViewById(R.id.MAC_RSSI);
        for (Map.Entry<String, String> entry : db.entrySet()) {
            TableRow tr_head = new TableRow(getApplicationContext());

            TextView MAC_view = new TextView(getApplicationContext());
            LinearLayout.LayoutParams MAC_params = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
            MAC_params.setMargins(0, 1, 1, 1);
            MAC_view.setLayoutParams(MAC_params);
            MAC_view.setText(entry.getKey());
            MAC_view.setTextColor(Color.BLACK);
            MAC_view.setGravity(Gravity.CENTER);
            MAC_view.setBackgroundColor(Color.WHITE);
            tr_head.addView(MAC_view);

            TextView RSSI_view = new TextView(getApplicationContext());
            LinearLayout.LayoutParams RSSI_params = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
            RSSI_params.setMargins(1, 1, 1, 1);
            RSSI_view.setLayoutParams(RSSI_params);
            RSSI_view.setText(String.valueOf(entry.getValue()));
            RSSI_view.setTextColor(Color.BLACK);
            RSSI_view.setGravity(Gravity.CENTER);
            RSSI_view.setBackgroundColor(Color.WHITE);
            tr_head.addView(RSSI_view);

            tl.addView(tr_head, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
        }

        File file = getFileStreamPath("addresses.txt");
        if (file != null && file.exists()) {

            try {
                FileInputStream fis = openFileInput("addresses.txt");
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bf = new BufferedReader(isr);

                // BufferedReader bf = new BufferedReader(new FileReader("addresses.txt"));
                String line = "";
                while (true) {
                    try {
                        if (!((line = bf.readLine()) != null)) break;
                        Log.i("Address", line);
                        stored_addresses.add(line);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                bf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ScanBLEDevices();
    }

    public void saveAddress(View view){
        final EditText addressView = (EditText) findViewById(R.id.inputAddress);
        DEVICE_ADDRESS_FILTER = addressView.getText().toString();
        stored_addresses.add(DEVICE_ADDRESS_FILTER);

        try {
            FileOutputStream outputStream = openFileOutput("addresses.txt", MODE_APPEND);
            outputStream.write((DEVICE_ADDRESS_FILTER + "\n").getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //ScanBLEDevices();  // TODO: need to uncomment later
    }

    public void removeAddresses(View view) {
        try {
            FileOutputStream outputStream = openFileOutput("addresses.txt", MODE_PRIVATE);
            outputStream.write(("").getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void ScanBLEDevices(){

        // Prepare the bluetooth adapter and scanner
        BluetoothLeScanner bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not, displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        bluetoothLeScanner.startScan(bleScanCallback);
    }

    // BLE Device scan callback.
    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (stored_addresses.contains(result.getDevice().getAddress())) {
                String deviceID = result.getDevice().getAddress();


                // infers distance by RSSI
                int temp = result.getRssi();
                String deviceRSSI = "";
                if(temp > -50){
                    deviceRSSI = "Less than 1 meter away";
                }else if(temp >-60){
                    deviceRSSI = "Less than 2 meter away";
                }else{
                    deviceRSSI = "More than 2 meters away";
                }
                String finalDeviceRSSI = deviceRSSI;
                if (!db.containsKey(deviceID)) {
                    // new device, add row to table
                    db.put(deviceID, deviceRSSI);
                    runOnUiThread(new Runnable() {
                        //@Override
                        public void run() {
                            // update UI for num of devices
                            TextView tv = (TextView) findViewById(R.id.NumDevices);
                            tv.setText("Devices Found: " + db.size());

                            // update UI for MAC-RSSI database
                            TableLayout tl = (TableLayout) findViewById(R.id.MAC_RSSI);
                            TableRow tr_head = new TableRow(getApplicationContext());

                            TextView MAC_view = new TextView(getApplicationContext());
                            LinearLayout.LayoutParams MAC_params = new TableRow.LayoutParams(
                                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
                            MAC_params.setMargins(0, 1, 1, 1);
                            MAC_view.setLayoutParams(MAC_params);
                            MAC_view.setText(deviceID);
                            MAC_view.setTextColor(Color.parseColor("#000000"));
                            MAC_view.setAlpha(0.54f);
                            MAC_view.setGravity(Gravity.CENTER);
                            MAC_view.setBackgroundColor(Color.WHITE);
                            tr_head.addView(MAC_view);

                            TextView RSSI_view = new TextView(getApplicationContext());
                            LinearLayout.LayoutParams RSSI_params = new TableRow.LayoutParams(
                                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
                            RSSI_params.setMargins(1, 1, 1, 1);
                            RSSI_view.setLayoutParams(RSSI_params);
                            RSSI_view.setText(finalDeviceRSSI);
                            RSSI_view.setTextColor(Color.parseColor("#000000"));
                            RSSI_view.setAlpha(0.54f);
                            RSSI_view.setGravity(Gravity.CENTER);
                            RSSI_view.setBackgroundColor(Color.WHITE);
                            tr_head.addView(RSSI_view);

                            Button trigger_button = new Button(getApplicationContext());
                            LinearLayout.LayoutParams trigger_params = new TableRow.LayoutParams(
                                    TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
                            trigger_button.setLayoutParams(trigger_params);
                            // Determine if the incoming beacon is from acoustic or UWB board

                            byte[] beacon = result.getScanRecord().getBytes();
                            int acoustics_or_uwb = 0;
                            for(int i = 0; i < beacon.length && i < 25; i++){
                                Log.d("Beacon: ", "" + beacon[i]);
                                acoustics_or_uwb += beacon[i];
                            }
                            if (acoustics_or_uwb == 117){
                                trigger_button.setText("Activate Sound");
                                trigger_button.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        sendBeacon(v);
                                    }
                                });
                            }else {
                                trigger_button.setText("Activate UWB");
                                trigger_button.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        Intent intent = new Intent(BleScanner.this, UWBScanner.class);
                                        BleScanner.this.startActivity(intent);
                                    }
                                });
                            }

                            trigger_button.setAlpha(0.54f);
                            trigger_button.setGravity(Gravity.CENTER);
                            trigger_button.setBackgroundColor(Color.GREEN);
                            tr_head.addView(trigger_button);

                            tl.addView(tr_head, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
                            trigger_by_address.put(trigger_button, result.getScanRecord().getBytes());
                        }
                    });

                } else {
                    db.put(deviceID, deviceRSSI);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // update UI for existing MAC with new RSSI value
                            TableLayout tl = (TableLayout) findViewById(R.id.MAC_RSSI);
                            for (int i = 0; i < tl.getChildCount(); i++) {
                                TableRow tr = (TableRow) tl.getChildAt(i);
                                TextView tv1 = (TextView) tr.getChildAt(0);
                                TextView tv2 = (TextView) tr.getChildAt(1);
                                String curDeviceID = tv1.getText().toString();
                                if (curDeviceID.equals(deviceID)) {
                                    tv2.setText(finalDeviceRSSI);
                                }
                            }
                        }
                    });

                }
            }
        }
    };

    public void sendBeacon(View view) {
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter()
                .getBluetoothLeAdvertiser();
        ParcelUuid pUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));

        Log.e("trigger word", Arrays.toString(trigger_by_address.get(view)));

        byte[] value = new byte[6];
        for (int i = 0; i < 6; i++) {
            value[i] = trigger_by_address.get(view)[i + 9];
        }

        AdvertiseData data = (new AdvertiseData.Builder())
                .setIncludeDeviceName(true)
                .addServiceData(pUuid, value)
                .build();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(5000)
                .build();

        AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.d("BLE", "Advertising started");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);
    }
}
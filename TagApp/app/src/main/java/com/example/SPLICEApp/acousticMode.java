package com.example.SPLICEApp;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class acousticMode extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private BluetoothLeScanner bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter()
            .getBluetoothLeScanner();
    private boolean scanning;
    private Handler handler = new Handler();
    private long SCAN_PERIOD = 4000;

    // Bluefruit52: DA:63:34:EF:9C:66
    private String address = "DA:63:34:EF:9C:66";
    private HashSet<String> addresses_to_scan = new HashSet<>();
    private ArrayList<String> addresses_to_scan_list = new ArrayList<>();
    private HashMap<String, byte[]> trigger_by_address = new HashMap<>();
    private String address_to_trigger;
    private Spinner spinner;

    AdvertisingSet currentAdvertisingSet;

    public void scanDevices(View view) throws IOException {
        // Remove previously scanned device addresses
        addresses_to_scan.clear();
        addresses_to_scan_list.clear();

        // Read addresses to be scanned
        FileInputStream fis = openFileInput("addresses.txt");
        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader bufferedReader = new BufferedReader(isr);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            addresses_to_scan.add(line);
        }
        addresses_to_scan_list = new ArrayList<>(addresses_to_scan);

        for (String address : addresses_to_scan_list) {
            Log.e("saved address", address);
        }

        // Start scanning devices to achieve trigger data
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
        bluetoothLeScanner.startScan(bleScanCallback);
    }

    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            String scanned_address = result.getDevice().getAddress();
            if (addresses_to_scan.contains(scanned_address)) {
                addresses_to_scan.remove(scanned_address);
                trigger_by_address.put(scanned_address, result.getScanRecord().getBytes());

                // TODO: debug
                ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(
                        getApplicationContext(), android.R.layout.simple_spinner_dropdown_item,
                        addresses_to_scan_list);
                spinnerArrayAdapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(spinnerArrayAdapter);
            }
        }
    };

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        address_to_trigger = parent.getItemAtPosition(pos).toString();
        Log.e("selected address", address_to_trigger);
        Log.e("trigger word", Arrays.toString(trigger_by_address.get(address_to_trigger)));
        //for (int i = 0; i  < trigger_by_address.get(address_to_trigger).length; i++)
        //    Log.e("trigger word", String.format("%02x", trigger_by_address.get(address_to_trigger)[i]));
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void sendBeacon(View view) {
        // 9-24
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter()
                .getBluetoothLeAdvertiser();
        ParcelUuid pUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));

        byte[] value = new byte[6];
        for (int i = 0; i < 6; i++) {
            value[i] = trigger_by_address.get(address_to_trigger)[i + 9];
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
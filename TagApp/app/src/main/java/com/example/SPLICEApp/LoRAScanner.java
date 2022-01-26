package com.example.SPLICEApp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class LoRAScanner extends AppCompatActivity {
    TextView tv;
    private int deviceId, portNum, baudRate;
    private static final String TAG = "SERIAL";
    private static final String T = "TEST";
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private Map<String,Long> map = new HashMap<String,Long>(); //race condition between adding and removing

    static class ListItem {
        UsbDevice device;
        int port;
        UsbSerialDriver driver;

        ListItem(UsbDevice device, int port, UsbSerialDriver driver) {
            this.device = device;
            this.port = port;
            this.driver = driver;
        }
    }

    private final ArrayList<ListItem> listItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lora_scanner);

        UsbSerialPort port = null;
        try {
            port = connect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (port == null){
            Toast.makeText(this, "Connection to LoRA adapter failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }


        Handler handler =new Handler();
        UsbSerialPort finalPort = port;
        final Runnable r = new Runnable() {

            public void run() {
                handler.postDelayed(this, 500);

                int len = 0;
                byte buffer[] = new byte[8192];
                String message = "";
                long time;
                try {
                    Log.d(TAG, "READ");
                    Log.d(TAG, String.valueOf(finalPort.isOpen()));
                    len = finalPort.read(buffer, 2000);
                    len = finalPort.read(buffer, 2000);
                    if (len != 0) {
                        message = message + receive(Arrays.copyOf(buffer, len));
                    }
                    len = finalPort.read(buffer, 2000);
                    len = finalPort.read(buffer, 2000);
                    len = finalPort.read(buffer, 2000);
                    if (len != 0) {
                        message = message + receive(Arrays.copyOf(buffer, len));
                        time = Calendar.getInstance().getTimeInMillis();
                        if(message.length() == 4){
                            map.put(message, time);
                        }
                    }
                    time = Calendar.getInstance().getTimeInMillis();
                    map.put(message, time);
                    Log.d(TAG, "Read " + len + " bytes.");

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        handler.postDelayed(r, 0000);
        long time = Calendar.getInstance().getTimeInMillis();
        Handler handler2 =new Handler();
        final Runnable r2 = new Runnable() {

            public void run() {
                handler2.postDelayed(this, 10000);
                // update UI for MAC-RSSI database
                int counter = 0;
                long time_now = Calendar.getInstance().getTimeInMillis();
                TableLayout tl = (TableLayout) findViewById(R.id.device_list);
                while (tl.getChildCount() > 1) {
                    TableRow row =  (TableRow)tl.getChildAt(1);
                    tl.removeView(row);
                    int j=tl.getChildCount();
                }
                for(Iterator<Map.Entry<String, Long>> it = map.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Long> entry = it.next();
                    long k_time = entry.getValue();
                    long time_d = time_now - k_time;
                    counter += 1;
                    if(time_d > 10000) {
                        it.remove();
                        counter -= 1;
                    }
                    else{
                        update_table(entry.getKey());
                    }
                }

                TextView tv = (TextView) findViewById(R.id.numDevices);
                tv.setText("Devices Found: " + counter);
            }
        };
        handler2.postDelayed(r2, 0000);
    }

    private void update_table(String text) {
        TableLayout tl = (TableLayout) findViewById(R.id.device_list);
        TableRow tr_head = new TableRow(LoRAScanner.this);

        TextView MAC_view = new TextView(LoRAScanner.this);
        LinearLayout.LayoutParams MAC_params = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
        MAC_params.setMargins(0, 1, 1, 1);
        MAC_view.setLayoutParams(MAC_params);
        MAC_view.setText(text);
        MAC_view.setTextColor(Color.parseColor("#000000"));
        MAC_view.setAlpha(0.54f);
        MAC_view.setGravity(Gravity.CENTER);
        MAC_view.setBackgroundColor(Color.WHITE);
        tr_head.addView(MAC_view);

        tl.addView(tr_head, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
    }

    private String receive(byte[] data) {

        String message = dumpHexString(data);

        return message;

    }
    
    public static String dumpHexString(byte[] array) {
        StringBuilder result = new StringBuilder();

        byte[] line = new byte[8];
        int lineIndex = 0;

        for (int i = 0; i < 0 + array.length; i++) {
            if (lineIndex == line.length) {
                for (int j = 0; j < line.length; j++) {
                    if (line[j] > ' ' && line[j] < '~') {
                        result.append(new String(line, j, 1));
                    } else {
                        result.append(".");
                    }
                }

                result.append("\n");
                lineIndex = 0;
            }

            byte b = array[i];
            line[lineIndex++] = b;
        }

        for (int i = 0; i < lineIndex; i++) {
            if (line[i] > ' ' && line[i] < '~') {
                result.append(new String(line, i, 1));
            } else {
                result.append(" ");
            }
        }


        Log.d(TAG, result.toString());

        return result.toString();
    }

    static UsbSerialProber getCustomProber() {
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x239A, 0x800B, CdcAcmSerialDriver.class); 
        customTable.addProduct(0x1366, 0x0105, CdcAcmSerialDriver.class);
        return new UsbSerialProber(customTable);
    }

    private UsbSerialPort LoRA() throws IOException {
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = getCustomProber();

        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x239A, 0x800B, CdcAcmSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);
        List<UsbSerialDriver> drivers = prober.findAllDrivers(manager);

        if (drivers.isEmpty()) {
            Log.d(TAG, "there");
            Toast.makeText(this, "This device doesn't support USB.", Toast.LENGTH_LONG).show();
            finish();
        }


        // Open a connection to the first available driver.
        UsbSerialDriver driver = drivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            Toast.makeText(this, "This device doesn't support USB.", Toast.LENGTH_LONG).show();
            finish();
        }

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        port.open(connection);
        port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        return port;
    }

    private UsbSerialPort connect() throws IOException {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = getCustomProber();
        listItems.clear();
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
            if(driver == null) {
                driver = usbCustomProber.probeDevice(device);
            }
            if(driver != null) {
                for(int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new ListItem(device, port, driver));
            } else {
                listItems.add(new ListItem(device, 0, null));
            }
        }

        UsbDevice device = null;
        if (listItems.isEmpty()) {
            return null;
        }
        device = listItems.get(0).device;

        int productId = device.getProductId();

        if (productId != 32779) {
            return null;
        }

        if(device == null) {
            return null;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            return null;
        }
        if(driver.getPorts().size() < listItems.get(0).port) {
            return null;
        }

        if(!usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
        }

        UsbSerialPort usbSerialPort = driver.getPorts().get(listItems.get(0).port);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());

        Log.d(TAG, String.valueOf(usbManager.hasPermission(driver.getDevice())));
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(115200, 8, 1, UsbSerialPort.PARITY_NONE);
            usbSerialPort.setDTR(true);
            Log.d(TAG, "connected");

        } catch (Exception e) {
            Log.d(TAG, "connection failed: " + e.getMessage());
            return null;
        }

        return usbSerialPort;
    }
}

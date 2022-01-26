package com.example.SPLICEApp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class UWBScanner extends AppCompatActivity {
    TextView tv;
    private int deviceId, portNum, baudRate;
    private static final String TAG = "SERIAL";
    private static final String T = "TEST";
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

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
        setContentView(R.layout.uwb_scanner);
        TextView text = (TextView) findViewById(R.id.textView);

        UsbSerialPort port = null;
        try {
            port = connect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (port == null){
            Toast.makeText(this, "Connection to UWB adapter failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }


        Handler handler =new Handler();
        UsbSerialPort finalPort = port;
        final Runnable r = new Runnable() {

            public void run() {
                final int random = new Random().nextInt(61);
                String t = String.valueOf(random);
                handler.postDelayed(this, 100);

                int len = 0;
                byte buffer[] = new byte[8192];
                try { //lora 3
                    Log.d(TAG, "READ");
                    Log.d(TAG, String.valueOf(finalPort.isOpen()));
                    len = finalPort.read(buffer, 2000);
                    Log.d(TAG, "Read " + len + " bytes.");
                    if (len == 6) {
                        receive(Arrays.copyOf(buffer, len), text);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }



            }
        };
        handler.postDelayed(r, 0000);
    }



    private void receive(byte[] data, TextView txt) {

        String message = dumpHexString(data);

        txt.setText(message);

    }
    private final static char[] HEX_DIGITS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };
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
        customTable.addProduct(0x239A, 0x800B, CdcAcmSerialDriver.class); // e.g. Digispark CDC
        customTable.addProduct(0x1366, 0x0105, CdcAcmSerialDriver.class); // e.g. Digispark CDC
        return new UsbSerialProber(customTable);
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

        if (productId != 261) {
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

        Log.d(TAG, "Screw you guys, Im going home");
        return usbSerialPort;
    }



}
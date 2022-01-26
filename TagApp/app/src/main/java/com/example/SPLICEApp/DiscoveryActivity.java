package com.example.SPLICEApp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class DiscoveryActivity extends AppCompatActivity {

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.discovery_menu);

    }

    public void switchBleScanner(View view) {
        Intent intent = new Intent(this, BleScanner.class);
        startActivity(intent);
    }

    public void switchLoRAScanner(View view) {
        Intent intent = new Intent(this, LoRAScanner.class);
        startActivity(intent);
    }

}
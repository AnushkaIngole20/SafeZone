package com.example.safezone;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class EmergencyServicesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_services);

        TextView ambulance = findViewById(R.id.btnAmbulance);
        TextView police = findViewById(R.id.btnPolice);
        TextView fire = findViewById(R.id.btnFire);
        TextView hospitals = findViewById(R.id.btnHospitals);
        TextView emergencyMap = findViewById(R.id.btnEmergencyMap);

        ambulance.setOnClickListener(v -> openDialer("108"));

        police.setOnClickListener(v -> openDialer("112"));

        fire.setOnClickListener(v -> openDialer("101"));

        hospitals.setOnClickListener(v ->
                openMaps("hospitals near me")
        );

        emergencyMap.setOnClickListener(v ->
                openMaps("emergency services near me")
        );
    }

    private void openDialer(String number) {

        Intent intent = new Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:" + number)
        );

        startActivity(intent);
    }

    private void openMaps(String search) {

        Uri uri = Uri.parse(
                "https://www.google.com/maps/search/?api=1&query="
                        + Uri.encode(search)
        );

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to open Maps",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
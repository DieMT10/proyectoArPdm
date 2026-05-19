package com.example.proyectoarpdm.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.proyectoarpdm.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private Button btnAbrirAR;
    private TextView txtEstadoGPS, txtDistancia;
    private FusedLocationProviderClient fusedLocationClient;

    private final LatLng puntoEducativo = new LatLng(13.4833, -88.1833);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        btnAbrirAR = findViewById(R.id.btnAbrirAR);
        txtEstadoGPS = findViewById(R.id.txtEstadoGPS);
        txtDistancia = findViewById(R.id.txtDistancia);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // BOTON ABRIR DETALLE

        btnAbrirAR.setOnClickListener(v -> {

            Intent intent = new Intent(
                    MapActivity.this,
                    DetailPointActivity.class
            );

            startActivity(intent);

        });

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        mMap = googleMap;

        mMap.addMarker(new MarkerOptions()
                .position(puntoEducativo)
                .title("Punto Educativo")
                .snippet("Zona educativa interactiva"));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(puntoEducativo, 15));

        obtenerUbicacionActual();
    }

    private void obtenerUbicacionActual() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST
            );

            return;
        }

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {

                    if (location != null) {

                        calcularDistancia(location);

                    } else {

                        txtEstadoGPS.setText("No se pudo obtener la ubicación");
                        txtDistancia.setText("Activa GPS e internet");

                    }

                });
    }

    private void calcularDistancia(Location ubicacionUsuario) {

        Location ubicacionPunto = new Location("puntoEducativo");

        ubicacionPunto.setLatitude(puntoEducativo.latitude);
        ubicacionPunto.setLongitude(puntoEducativo.longitude);

        float distancia = ubicacionUsuario.distanceTo(ubicacionPunto);

        txtDistancia.setText(
                "📍 Distancia: " + Math.round(distancia) + " metros"
        );

        if (distancia <= 50) {

            txtEstadoGPS.setText("✅ Punto educativo cercano");

        } else {

            txtEstadoGPS.setText("📌 Punto educativo detectado");

        }

        LatLng miUbicacion = new LatLng(
                ubicacionUsuario.getLatitude(),
                ubicacionUsuario.getLongitude()
        );

        mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(miUbicacion, 16)
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_PERMISSION_REQUEST) {

            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                obtenerUbicacionActual();

            } else {

                Toast.makeText(
                        this,
                        "Permiso de ubicación denegado",
                        Toast.LENGTH_SHORT
                ).show();

            }
        }
    }
}
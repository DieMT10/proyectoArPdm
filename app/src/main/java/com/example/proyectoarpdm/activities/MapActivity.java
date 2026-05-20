package com.example.proyectoarpdm.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.Activities.ModelListActivity;
import com.example.proyectoarpdm.Activities.modelARActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private Button btnAbrirAR;
    private TextView txtEstadoGPS, txtDistancia;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private String modelId, modelName, modelUrl;
    private ArrayList<String> slides;
    private LatLng puntoDestino;
    private boolean isCloseEnough = false;
    private boolean hasVibratedForThisPoint = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Log.d(TAG, "onCreate: Iniciando MapActivity");

        // Recibir datos del modelo
        modelId = getIntent().getStringExtra("model_id");
        modelName = getIntent().getStringExtra("model_name");
        modelUrl = getIntent().getStringExtra("model_url");
        String lat = getIntent().getStringExtra("latitud");
        String lon = getIntent().getStringExtra("longitud");
        slides = getIntent().getStringArrayListExtra("slides");

        // Coordenadas por defecto (San Salvador por ejemplo) si no vienen en el intent
        if (lat != null && lon != null && !lat.trim().isEmpty() && !lon.trim().isEmpty()) {
            try {
                puntoDestino = new LatLng(Double.parseDouble(lat), Double.parseDouble(lon));
                Log.d(TAG, "Destino recibido: " + lat + ", " + lon);
            } catch (NumberFormatException e) {
                puntoDestino = new LatLng(13.6893, -89.1872); 
            }
        } else {
            Log.d(TAG, "Sin coordenadas en intent, usando ubicación por defecto");
            puntoDestino = new LatLng(13.6893, -89.1872); 
        }

        btnAbrirAR = findViewById(R.id.btnAbrirAR);
        txtEstadoGPS = findViewById(R.id.txtEstadoGPS);
        txtDistancia = findViewById(R.id.txtDistancia);

        btnAbrirAR.setEnabled(false);
        btnAbrirAR.setAlpha(0.5f);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnAbrirAR.setOnClickListener(v -> {
            if (isCloseEnough) {
                downloadAndOpenModel();
            } else {
                Toast.makeText(this, "Acércate más para activar la experiencia", Toast.LENGTH_SHORT).show();
            }
        });

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        calcularDistancia(location);
                    }
                }
            }
        };

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            Log.d(TAG, "Cargando fragmento de mapa...");
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "Error: No se encontró el fragmento con ID R.id.map");
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_map);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_models) {
                startActivity(new Intent(this, ModelListActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return itemId == R.id.nav_map;
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d(TAG, "onMapReady: El mapa está listo");
        mMap = googleMap;
        
        // Configuración visual del mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        
        mMap.addMarker(new MarkerOptions()
                .position(puntoDestino)
                .title(modelName != null ? modelName : "Punto Educativo")
                .snippet("Zona interactiva de RA"));
        
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(puntoDestino, 15));
        
        startLocationUpdates();
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        if (mMap != null) {
            mMap.setMyLocationEnabled(true);
            Log.d(TAG, "Ubicación en el mapa activada");
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void calcularDistancia(Location ubicacionUsuario) {
        Location ubicacionPunto = new Location("puntoDestino");
        ubicacionPunto.setLatitude(puntoDestino.latitude);
        ubicacionPunto.setLongitude(puntoDestino.longitude);

        float distancia = ubicacionUsuario.distanceTo(ubicacionPunto);
        txtDistancia.setText(String.format("Distancia: %d metros", Math.round(distancia)));

        if (distancia <= 50) {
            txtEstadoGPS.setText("¡Llegaste al punto educativo!");
            if (!isCloseEnough && !hasVibratedForThisPoint) {
                vibrarYNotificar();
            }
            isCloseEnough = true;
            btnAbrirAR.setEnabled(true);
            btnAbrirAR.setAlpha(1.0f);
        } else {
            txtEstadoGPS.setText("Buscando punto educativo...");
            isCloseEnough = false;
            btnAbrirAR.setEnabled(false);
            btnAbrirAR.setAlpha(0.5f);
            hasVibratedForThisPoint = false;
        }
    }

    private void vibrarYNotificar() {
        hasVibratedForThisPoint = true;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("¡Punto detectado!")
                .setMessage("¿Deseas abrir la cámara RA para ver el contenido?")
                .setPositiveButton("Abrir Cámara", (dialog, which) -> downloadAndOpenModel())
                .setNegativeButton("Más tarde", null)
                .show();
    }

    private void downloadAndOpenModel() {
        if (modelUrl == null || modelUrl.isEmpty()) {
            Toast.makeText(this, "Error: Debes seleccionar un modelo primero en la lista", Toast.LENGTH_LONG).show();
            return;
        }

        btnAbrirAR.setEnabled(false);
        btnAbrirAR.setText("Cargando...");
        
        File localFile = new File(getCacheDir(), modelId + ".glb");
        if (localFile.exists()) {
            openARActivity(localFile.getAbsolutePath());
            return;
        }

        if (modelUrl.startsWith("gs://") || modelUrl.contains("firebasestorage.googleapis.com")) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(modelUrl);
            storageRef.getFile(localFile).addOnSuccessListener(taskSnapshot -> openARActivity(localFile.getAbsolutePath())).addOnFailureListener(exception -> {
                btnAbrirAR.setEnabled(true);
                btnAbrirAR.setText("🚀 Abrir Experiencia RA");
                Toast.makeText(MapActivity.this, "Error al descargar modelo", Toast.LENGTH_SHORT).show();
            });
        } else {
            downloadFromExternalUrl(modelUrl, localFile);
        }
    }

    private void downloadFromExternalUrl(String urlString, File destination) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(urlString.replace(" ", "%20"));
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.connect();
                try (java.io.InputStream input = connection.getInputStream();
                     java.io.OutputStream output = new java.io.FileOutputStream(destination)) {
                    byte[] data = new byte[8192];
                    int count;
                    while ((count = input.read(data)) != -1) {
                        output.write(data, 0, count);
                    }
                }
                runOnUiThread(() -> openARActivity(destination.getAbsolutePath()));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnAbrirAR.setEnabled(true);
                    btnAbrirAR.setText("🚀 Abrir Experiencia RA");
                    Toast.makeText(this, "Error de red", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openARActivity(String modelPath) {
        Intent intent = new Intent(this, modelARActivity.class);
        intent.putExtra("model_path", modelPath);
        if (slides != null) {
            intent.putStringArrayListExtra("slides", slides);
        }
        startActivity(intent);
        btnAbrirAR.setEnabled(true);
        btnAbrirAR.setText("🚀 Abrir Experiencia RA");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }
}

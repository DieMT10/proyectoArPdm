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
import com.google.android.gms.maps.model.Marker;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.example.proyectoarpdm.models.ARModel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private Button btnAbrirAR;
    private TextView txtEstadoGPS, txtDistancia;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference databaseReference;
    private Map<Marker, ARModel> markerModelMap = new HashMap<>();

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
        databaseReference = FirebaseDatabase.getInstance().getReference("models");

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

        loadLessonsFromFirebase();

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

    private void loadLessonsFromFirebase() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mMap == null) return;
                
                // Limpiar marcadores previos (excepto la ubicación del usuario que es nativa)
                for (Marker marker : markerModelMap.keySet()) {
                    marker.remove();
                }
                markerModelMap.clear();

                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    ARModel model = postSnapshot.getValue(ARModel.class);
                    if (model != null) {
                        model.setId(postSnapshot.getKey());
                        String latStr = model.getLatitud();
                        String lonStr = model.getLongitud();

                        if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                            try {
                                double lat = Double.parseDouble(latStr);
                                double lon = Double.parseDouble(lonStr);
                                LatLng pos = new LatLng(lat, lon);

                                Marker marker = mMap.addMarker(new MarkerOptions()
                                        .position(pos)
                                        .title(model.getName() != null ? model.getName() : postSnapshot.getKey())
                                        .snippet("Toca para ver detalles"));
                                
                                markerModelMap.put(marker, model);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error de formato en coordenadas para: " + postSnapshot.getKey());
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error cargando lecciones: " + error.getMessage());
            }
        });
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        ARModel model = markerModelMap.get(marker);
        if (model != null) {
            // Actualizar el destino actual al marker seleccionado
            puntoDestino = marker.getPosition();
            modelId = model.getId();
            modelName = model.getName();
            modelUrl = model.getModelUrl();
            slides = new ArrayList<>(model.getSlides());
            
            marker.showInfoWindow();
            
            // Forzar recálculo de distancia con la nueva lección
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                    if (location != null) {
                        calcularDistancia(location);
                    }
                });
            }
            
            Toast.makeText(this, "Objetivo: " + modelName, Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d(TAG, "onMapReady: El mapa está listo");
        mMap = googleMap;
        
        mMap.setOnMarkerClickListener(this);
        
        // Configuración visual del mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        
        // Mover cámara a la ubicación por defecto o la recibida
        if (puntoDestino != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(puntoDestino, 15));
        }
        
        startLocationUpdates();
        // Los marcadores se cargarán automáticamente a través del listener de Firebase en onCreate
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
        if (puntoDestino == null) return;

        Location ubicacionPunto = new Location("puntoDestino");
        ubicacionPunto.setLatitude(puntoDestino.latitude);
        ubicacionPunto.setLongitude(puntoDestino.longitude);

        float distancia = ubicacionUsuario.distanceTo(ubicacionPunto);
        
        // Mostrar coordenadas y distancia
        String info = String.format("Mi ubicación: %.4f, %.4f\nDistancia: %d metros", 
                ubicacionUsuario.getLatitude(), 
                ubicacionUsuario.getLongitude(), 
                Math.round(distancia));
        txtDistancia.setText(info);

        if (distancia <= 50) {
            txtEstadoGPS.setText("¡Estás cerca de: " + (modelName != null ? modelName : "el punto") + "!");
            if (!isCloseEnough && !hasVibratedForThisPoint) {
                vibrarYNotificar();
            }
            isCloseEnough = true;
            btnAbrirAR.setEnabled(true);
            btnAbrirAR.setAlpha(1.0f);
        } else {
            txtEstadoGPS.setText("Objetivo: " + (modelName != null ? modelName : "Selecciona un punto"));
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

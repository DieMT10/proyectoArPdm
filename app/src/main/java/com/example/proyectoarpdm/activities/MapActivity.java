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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.helloar.HelloArActivity;
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

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, SensorEventListener {

    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private Button btnAbrirAR, btnNavMode;
    private View navOverlay;
    private ImageView navArrow, navModelIcon;
    private TextView txtEstadoGPS, txtDistancia, navTxtDist;
    
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float currentAzimuth = 0f;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference databaseReference;
    private Map<Marker, ARModel> markerModelMap = new HashMap<>();
    private List<ARModel> tempModelList = new ArrayList<>();

    private ARModel selectedModel;
    private String modelId, modelName, modelUrl, slidesUrl;
    private ArrayList<String> slides;
    private LatLng puntoDestino;
    private Location currentUbi;
    private boolean isCloseEnough = false;
    private boolean hasVibratedForThisPoint = false;
    private boolean isFirstLocationUpdate = true;
    private boolean isNavModeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        btnAbrirAR = findViewById(R.id.btnAbrirAR);
        btnNavMode = findViewById(R.id.btnNavMode);
        navOverlay = findViewById(R.id.nav_overlay);
        navArrow = findViewById(R.id.nav_arrow);
        navModelIcon = findViewById(R.id.nav_model_icon);
        navTxtDist = findViewById(R.id.nav_txt_dist);
        txtEstadoGPS = findViewById(R.id.txtEstadoGPS);
        txtDistancia = findViewById(R.id.txtDistancia);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        databaseReference = FirebaseDatabase.getInstance().getReference("models");

        setupNavigation();
        initLocationCallback();
        
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        loadLessonsFromFirebase();

        btnAbrirAR.setOnClickListener(v -> {
            if (isCloseEnough) downloadAndOpenModel();
            else Toast.makeText(this, "Acércate a menos de 50m", Toast.LENGTH_SHORT).show();
        });

        btnNavMode.setOnClickListener(v -> {
            if (puntoDestino == null) {
                Toast.makeText(this, "Selecciona un punto en el mapa primero", Toast.LENGTH_SHORT).show();
                return;
            }
            isNavModeActive = !isNavModeActive;
            navOverlay.setVisibility(isNavModeActive ? View.VISIBLE : View.GONE);
            btnNavMode.setText(isNavModeActive ? "❌ Cancelar Búsqueda" : "📍 Iniciar Búsqueda");
            
            if (isNavModeActive && selectedModel != null) {
                Glide.with(this).load(selectedModel.getImagen()).circleCrop().into(navModelIcon);
            }
        });
    }

    private void setupNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_map);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_models) {
                startActivity(new Intent(this, ModelListActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_add) {
                startActivity(new Intent(this, AddResourceActivity.class));
                finish();
                return true;
            }
            return itemId == R.id.nav_map;
        });
    }

    private void initLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) calcularDistancia(location);
                }
            }
        };
    }

    private void loadLessonsFromFirebase() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tempModelList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    ARModel model = postSnapshot.getValue(ARModel.class);
                    if (model != null) {
                        model.setId(postSnapshot.getKey());
                        
                        // INTELIGENCIA: Si "slides" contiene una URL, usarla como slidesUrl
                        List<String> slidesContent = model.getSlides();
                        if (!slidesContent.isEmpty()) {
                            String first = slidesContent.get(0);
                            if (first != null && (first.startsWith("http") || first.startsWith("gs://"))) {
                                model.setSlidesUrl(first);
                                model.setSlides(new ArrayList<String>()); 
                                Log.d(TAG, "Detectada URL en slides para mapa: " + first);
                            }
                        }

                        if (postSnapshot.hasChild("slidesUrl")) {
                            Object urlVal = postSnapshot.child("slidesUrl").getValue();
                            model.setSlidesUrl(urlVal != null ? String.valueOf(urlVal) : "");
                        }

                        tempModelList.add(model);
                    }
                }
                // Si el mapa ya está listo, dibujar ahora mismo
                if (mMap != null) updateMapMarkers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error Firebase: " + error.getMessage());
            }
        });
    }

    private void updateMapMarkers() {
        if (mMap == null) return;
        mMap.clear();
        markerModelMap.clear();

        for (ARModel model : tempModelList) {
            String latStr = model.getLatitud();
            String lonStr = model.getLongitud();

            if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                try {
                    LatLng pos = new LatLng(Double.parseDouble(latStr), Double.parseDouble(lonStr));
                    
                    // Crear marcador con icono personalizado
                    MarkerOptions options = new MarkerOptions()
                            .position(pos)
                            .title(model.getName() != null ? model.getName() : model.getId())
                            .snippet("Objetivo educativo");

                    // Intentar cargar la imagen personalizada como icono
                    if (model.getImagen() != null && !model.getImagen().isEmpty()) {
                        Glide.with(this)
                                .asBitmap()
                                .load(model.getImagen())
                                .override(100, 100) // Tamaño del icono
                                .into(new CustomTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                        Marker marker = mMap.addMarker(options.icon(BitmapDescriptorFactory.fromBitmap(resource)));
                                        if (marker != null) markerModelMap.put(marker, model);
                                    }
                                    @Override
                                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                                });
                    } else {
                        // Fallback a icono por defecto si no hay imagen
                        Marker marker = mMap.addMarker(options);
                        if (marker != null) markerModelMap.put(marker, model);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error en coordenadas de " + model.getId());
                }
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            // Intentar obtener la última ubicación conocida para centrar rápido
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null && isFirstLocationUpdate) {
                    LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                    isFirstLocationUpdate = false;
                }
            });
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        }

        updateMapMarkers(); // Dibujar lo que ya se haya cargado de Firebase
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        selectedModel = markerModelMap.get(marker);
        if (selectedModel != null) {
            puntoDestino = marker.getPosition();
            modelId = selectedModel.getId();
            modelName = selectedModel.getName();
            modelUrl = selectedModel.getModelUrl();
            slidesUrl = selectedModel.getSlidesUrl();
            slides = new ArrayList<>(selectedModel.getSlides());
            
            marker.showInfoWindow();
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(puntoDestino, 16));
            
            Toast.makeText(this, "Objetivo: " + modelName, Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isNavModeActive || puntoDestino == null || currentUbi == null) return;
        float azimuth = event.values[0]; 
        Location targetLoc = new Location("target");
        targetLoc.setLatitude(puntoDestino.latitude);
        targetLoc.setLongitude(puntoDestino.longitude);
        float bearing = currentUbi.bearingTo(targetLoc);
        float rotation = bearing - azimuth;
        navArrow.setRotation(rotation);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void calcularDistancia(Location miUbi) {
        currentUbi = miUbi;
        if (mMap != null && isFirstLocationUpdate) {
            LatLng userLatLng = new LatLng(miUbi.getLatitude(), miUbi.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
            isFirstLocationUpdate = false;
        }

        if (puntoDestino == null) {
            txtDistancia.setText(String.format("Tu ubicación: %.4f, %.4f", miUbi.getLatitude(), miUbi.getLongitude()));
            txtEstadoGPS.setText("Selecciona un punto en el mapa");
            return;
        }

        Location target = new Location("target");
        target.setLatitude(puntoDestino.latitude);
        target.setLongitude(puntoDestino.longitude);

        float dist = miUbi.distanceTo(target);
        txtDistancia.setText(String.format("GPS: %.4f, %.4f | Distancia: %dm", 
                miUbi.getLatitude(), miUbi.getLongitude(), Math.round(dist)));
        
        if (isNavModeActive) {
            navTxtDist.setText(Math.round(dist) + " m");
        }

        if (dist <= 50) {
            txtEstadoGPS.setText("¡Llegaste a: " + modelName + "!");
            if (!isCloseEnough && !hasVibratedForThisPoint) {
                vibrarYNotificar();
                if (isNavModeActive) downloadAndOpenModel();
            }
            isCloseEnough = true;
            btnAbrirAR.setEnabled(true);
            btnAbrirAR.setAlpha(1.0f);
        } else {
            txtEstadoGPS.setText("Dirígete a: " + modelName);
            isCloseEnough = false;
            btnAbrirAR.setEnabled(false);
            btnAbrirAR.setAlpha(0.5f);
            hasVibratedForThisPoint = false;
        }
    }

    private void vibrarYNotificar() {
        hasVibratedForThisPoint = true;
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(500, 255));
            else v.vibrate(500);
        }
        new AlertDialog.Builder(this)
                .setTitle("¡Llegaste!")
                .setMessage("¿Abrir contenido de " + modelName + "?")
                .setPositiveButton("Abrir RA", (d, w) -> downloadAndOpenModel())
                .setNegativeButton("Luego", null).show();
    }

    private void downloadAndOpenModel() {
        if (modelUrl == null || modelUrl.isEmpty()) return;
        btnAbrirAR.setEnabled(false);
        btnAbrirAR.setText("Descargando...");
        
        File file = new File(getCacheDir(), modelId + ".glb");
        if (file.exists()) {
            downloadSlidesIfNeeded(file.getAbsolutePath());
            return;
        }

        if (modelUrl.startsWith("gs://") || modelUrl.contains("firebasestorage")) {
            FirebaseStorage.getInstance().getReferenceFromUrl(modelUrl).getFile(file)
                .addOnSuccessListener(task -> downloadSlidesIfNeeded(file.getAbsolutePath()))
                .addOnFailureListener(e -> {
                    btnAbrirAR.setEnabled(true);
                    btnAbrirAR.setText("Reintentar RA");
                });
        } else {
            downloadExternal(modelUrl, file, true);
        }
    }

    private void downloadSlidesIfNeeded(String localModelPath) {
        if (slidesUrl == null || slidesUrl.isEmpty()) {
            openARActivity(localModelPath, null);
            return;
        }

        File localSlidesFile = new File(getCacheDir(), modelId + "_slides.pdf");
        if (localSlidesFile.exists()) {
            openARActivity(localModelPath, localSlidesFile.getAbsolutePath());
            return;
        }

        if (slidesUrl.startsWith("gs://") || slidesUrl.contains("firebasestorage")) {
            FirebaseStorage.getInstance().getReferenceFromUrl(slidesUrl).getFile(localSlidesFile)
                .addOnSuccessListener(task -> openARActivity(localModelPath, localSlidesFile.getAbsolutePath()))
                .addOnFailureListener(e -> {
                    // Si fallan las slides, abrir solo el modelo
                    openARActivity(localModelPath, null);
                });
        } else {
            downloadExternal(slidesUrl, localSlidesFile, false);
        }
    }

    private void downloadExternal(String url, File dest, boolean isModel) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url.replace(" ", "%20")).openConnection();
                c.connect();
                try (java.io.InputStream in = c.getInputStream(); java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] b = new byte[8192]; int r;
                    while ((r = in.read(b)) != -1) out.write(b, 0, r);
                }
                runOnUiThread(() -> {
                    if (isModel) downloadSlidesIfNeeded(dest.getAbsolutePath());
                    else openARActivity(dest.getParent() + "/" + modelId + ".glb", dest.getAbsolutePath());
                });
            } catch (Exception e) {
                runOnUiThread(() -> { 
                    btnAbrirAR.setEnabled(true); 
                    btnAbrirAR.setText(isModel ? "Error Red" : "Error Slides");
                    if (!isModel) openARActivity(dest.getParent() + "/" + modelId + ".glb", null);
                });
            }
        }).start();
    }

    private void openARActivity(String modelPath, String slidesPath) {
        Intent intent = new Intent(this, HelloArActivity.class);
        if (modelPath != null) intent.putExtra("model_path", modelPath);
        if (slidesPath != null) intent.putExtra("slides_url", slidesPath);
        if (slides != null) intent.putStringArrayListExtra("slides", slides);

        startActivity(intent);
        btnAbrirAR.setEnabled(true);
        btnAbrirAR.setText("🚀 Abrir Experiencia RA");
    }

    @Override
    protected void onResume() { 
        super.onResume(); 
        startLocationUpdates(); 
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() { 
        super.onPause(); 
        fusedLocationClient.removeLocationUpdates(locationCallback);
        sensorManager.unregisterListener(this);
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
            fusedLocationClient.requestLocationUpdates(req, locationCallback, null);
        }
    }
}

package com.example.proyectoarpdm.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.adapters.ModelAdapter;
import com.example.proyectoarpdm.models.ARModel;
import com.example.proyectoarpdm.activities.MapActivity;
import com.example.proyectoarpdm.activities.AddResourceActivity;
import com.example.proyectoarpdm.helloar.HelloArActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModelListActivity extends AppCompatActivity implements ModelAdapter.OnModelClickListener {

    private static final String TAG = "ModelListActivity";
    private ModelAdapter adapter;
    private List<ARModel> modelList;
    private ProgressBar progressBar;
    private DatabaseReference databaseReference;
    private ValueEventListener modelListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_list);

        progressBar = findViewById(R.id.progress_bar);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        modelList = new ArrayList<>();
        adapter = new ModelAdapter(modelList, this);
        recyclerView.setAdapter(adapter);

        databaseReference = FirebaseDatabase.getInstance().getReference("models");
        loadModels();

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_models);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_map) {
                startActivity(new Intent(this, MapActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.nav_add) {
                startActivity(new Intent(this, AddResourceActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return itemId == R.id.nav_models;
        });
    }

    private void loadModels() {
        progressBar.setVisibility(View.VISIBLE);
        modelListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                modelList.clear();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    ARModel model = postSnapshot.getValue(ARModel.class);
                    if (model != null) {
                        model.setId(postSnapshot.getKey());
                        if (model.getName() == null || model.getName().isEmpty()) {
                            model.setName(postSnapshot.getKey());
                        }

                        // LÓGICA DE DETECCIÓN DE URL EN CAMPO SLIDES
                        List<String> slidesContent = model.getSlides();
                        if (!slidesContent.isEmpty()) {
                            String first = slidesContent.get(0);
                            if (first != null && (first.startsWith("http") || first.startsWith("gs://"))) {
                                // Es una URL, la movemos al campo correcto y limpiamos la lista de textos
                                model.setSlidesUrl(first);
                                model.setSlides(new ArrayList<String>()); 
                                Log.d(TAG, "Detectada URL en campo slides para " + model.getName() + ": " + first);
                            }
                        }

                        if (postSnapshot.hasChild("slidesUrl")) {
                            Object urlVal = postSnapshot.child("slidesUrl").getValue();
                            model.setSlidesUrl(urlVal != null ? String.valueOf(urlVal) : "");
                        }
                        
                        modelList.add(model);
                    }
                }
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                
                if (modelList.isEmpty()) {
                    Toast.makeText(ModelListActivity.this, "No hay modelos disponibles", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Database error", error.toException());
                Toast.makeText(ModelListActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        databaseReference.addValueEventListener(modelListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseReference != null && modelListener != null) {
            databaseReference.removeEventListener(modelListener);
        }
    }

    @Override
    public void onModelClick(ARModel model) {
        downloadModel(model);
    }

    private void downloadModel(ARModel model) {
        String modelUrl = model.getModelUrl();
        if (modelUrl == null || modelUrl.isEmpty()) {
            Toast.makeText(this, "Error: URL del modelo no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.downloading_model, Toast.LENGTH_SHORT).show();

        File localModelFile = new File(getCacheDir(), model.getId() + ".glb");
        
        if (localModelFile.exists() && localModelFile.length() > 1024) {
            downloadSlidesIfNeeded(model, localModelFile.getAbsolutePath());
            return;
        }

        if (modelUrl.startsWith("gs://") || modelUrl.contains("firebasestorage.googleapis.com")) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(modelUrl);
            storageRef.getFile(localModelFile).addOnSuccessListener(taskSnapshot -> {
                downloadSlidesIfNeeded(model, localModelFile.getAbsolutePath());
            }).addOnFailureListener(exception -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ModelListActivity.this, R.string.download_failed, Toast.LENGTH_SHORT).show();
            });
        } else {
            downloadFileFromUrl(modelUrl, localModelFile, () -> downloadSlidesIfNeeded(model, localModelFile.getAbsolutePath()));
        }
    }

    private void downloadSlidesIfNeeded(ARModel model, String localModelPath) {
        String slidesUrl = model.getSlidesUrl();
        if (slidesUrl == null || slidesUrl.isEmpty()) {
            openARActivity(localModelPath, null, model.getSlides());
            return;
        }

        File localSlidesFile = new File(getCacheDir(), model.getId() + "_slides.pdf");
        // Si ya existe y es un PDF válido (más de 5KB)
        if (localSlidesFile.exists() && localSlidesFile.length() > 5000) {
            openARActivity(localModelPath, localSlidesFile.getAbsolutePath(), model.getSlides());
            return;
        }

        if (slidesUrl.startsWith("gs://") || slidesUrl.contains("firebasestorage.googleapis.com")) {
            StorageReference storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(slidesUrl);
            storageRef.getFile(localSlidesFile).addOnSuccessListener(taskSnapshot -> {
                openARActivity(localModelPath, localSlidesFile.getAbsolutePath(), model.getSlides());
            }).addOnFailureListener(exception -> {
                openARActivity(localModelPath, null, model.getSlides());
            });
        } else {
            downloadFileFromUrl(slidesUrl, localSlidesFile, () -> openARActivity(localModelPath, localSlidesFile.getAbsolutePath(), model.getSlides()));
        }
    }

    private void downloadFileFromUrl(String urlString, File destination, Runnable onSuccess) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Iniciando descarga AWS: " + urlString);
                // Codificar espacios si los hay
                String encodedUrl = urlString.replace(" ", "%20");
                java.net.URL url = new java.net.URL(encodedUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() != java.net.HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + connection.getResponseCode());
                }

                try (java.io.InputStream input = connection.getInputStream();
                     java.io.OutputStream output = new java.io.FileOutputStream(destination)) {
                    byte[] data = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = input.read(data)) != -1) {
                        output.write(data, 0, bytesRead);
                    }
                }
                Log.d(TAG, "Descarga exitosa: " + destination.getAbsolutePath());
                runOnUiThread(onSuccess);
            } catch (Exception e) {
                Log.e(TAG, "Error en descarga externa: " + urlString, e);
                runOnUiThread(() -> {
                    if (destination.getName().endsWith("_slides.pdf")) {
                         Toast.makeText(this, "Aviso: No se pudo descargar el PDF educativo", Toast.LENGTH_SHORT).show();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error de red al descargar modelo", Toast.LENGTH_SHORT).show();
                    }
                    // Intentar abrir la actividad aunque falle el PDF (usará fallback de texto)
                    onSuccess.run();
                });
            }
        }).start();
    }

    private void openARActivity(String modelPath, String slidesPath, List<String> slides) {
        progressBar.setVisibility(View.GONE);
        Intent intent = new Intent(this, HelloArActivity.class);
        intent.putExtra("model_path", modelPath);
        if (slidesPath != null) {
            intent.putExtra("slides_url", slidesPath);
        }
        if (slides != null && !slides.isEmpty()) {
            intent.putStringArrayListExtra("slides", new ArrayList<>(slides));
        }
        startActivity(intent);
    }
}

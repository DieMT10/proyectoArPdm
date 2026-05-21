package com.example.proyectoarpdm.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.common.helpers.S3Helper;
import com.example.proyectoarpdm.models.ARModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AddResourceActivity extends AppCompatActivity {

    private static final String TAG = "AddResourceActivity";
    private TextInputEditText etId, etName, etLat, etLon, etImgUrl, etModelUrl, etSlidesUrl;
    private ImageButton btnUploadImg, btnUploadModel, btnUploadSlides;
    private Button btnSave;
    private DatabaseReference databaseReference;

    private ActivityResultLauncher<String> filePickerLauncher;
    private String currentUploadType = ""; // "img", "model", "slides"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_resource);

        databaseReference = FirebaseDatabase.getInstance().getReference("models");

        etId = findViewById(R.id.et_id);
        etName = findViewById(R.id.et_name);
        etLat = findViewById(R.id.et_lat);
        etLon = findViewById(R.id.et_lon);
        etImgUrl = findViewById(R.id.et_img_url);
        etModelUrl = findViewById(R.id.et_model_url);
        etSlidesUrl = findViewById(R.id.et_slides_url);
        btnSave = findViewById(R.id.btn_save);

        btnUploadImg = findViewById(R.id.btn_upload_img);
        btnUploadModel = findViewById(R.id.btn_upload_model);
        btnUploadSlides = findViewById(R.id.btn_upload_slides);

        initFilePicker();

        btnUploadImg.setOnClickListener(v -> {
            currentUploadType = "img";
            filePickerLauncher.launch("image/*");
        });

        btnUploadModel.setOnClickListener(v -> {
            currentUploadType = "model";
            filePickerLauncher.launch("*/*");
        });

        btnUploadSlides.setOnClickListener(v -> {
            currentUploadType = "slides";
            filePickerLauncher.launch("application/pdf");
        });

        btnSave.setOnClickListener(v -> saveToFirebase());

        setupNavigation();
    }

    private void initFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadToS3(uri);
                    }
                }
        );
    }

    private void uploadToS3(Uri uri) {
        String id = etId.getText().toString().trim();
        if (id.isEmpty()) {
            Toast.makeText(this, "Primero ingresa el ID del modelo para crear la carpeta", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = getFileName(uri);
        Toast.makeText(this, "Subiendo " + fileName + "...", Toast.LENGTH_SHORT).show();

        S3Helper.uploadFile(this, uri, id, fileName, new S3Helper.UploadCallback() {
            @Override
            public void onProgress(int id, long bytesCurrent, long bytesTotal) {
                // Podrías implementar una barra de progreso aquí
            }

            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> {
                    Toast.makeText(AddResourceActivity.this, "¡Subida exitosa!", Toast.LENGTH_SHORT).show();
                    switch (currentUploadType) {
                        case "img": etImgUrl.setText(url); break;
                        case "model": etModelUrl.setText(url); break;
                        case "slides": etSlidesUrl.setText(url); break;
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "S3 Upload Error", e);
                    Toast.makeText(AddResourceActivity.this, "Error al subir: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void setupNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_add);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_models) {
                startActivity(new Intent(this, ModelListActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_map) {
                startActivity(new Intent(this, MapActivity.class));
                finish();
                return true;
            }
            return itemId == R.id.nav_add;
        });
    }

    private void saveToFirebase() {
        String id = etId.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String latStr = etLat.getText().toString().trim();
        String lonStr = etLon.getText().toString().trim();
        String imgUrl = etImgUrl.getText().toString().trim();
        String modelUrl = etModelUrl.getText().toString().trim();
        String slidesUrl = etSlidesUrl.getText().toString().trim();

        if (id.isEmpty() || name.isEmpty() || latStr.isEmpty() || lonStr.isEmpty() || modelUrl.isEmpty()) {
            Toast.makeText(this, "Por favor completa los campos obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);

            ARModel model = new ARModel();
            model.setName(name);
            model.setLatitud(lat);
            model.setLongitud(lon);
            model.setImagen(imgUrl);
            model.setModelUrl(modelUrl);
            model.setSlides(slidesUrl); // Se guarda como String (URL única)

            databaseReference.child(id).setValue(model)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddResourceActivity.this, "Recurso guardado correctamente", Toast.LENGTH_SHORT).show();
                        clearFields();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al guardar", e);
                        Toast.makeText(AddResourceActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Latitud y Longitud deben ser números válidos", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFields() {
        etId.setText("");
        etName.setText("");
        etLat.setText("");
        etLon.setText("");
        etImgUrl.setText("");
        etModelUrl.setText("");
        etSlidesUrl.setText("");
    }
}

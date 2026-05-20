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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("model_id", model.getId());
        intent.putExtra("model_name", model.getName());
        intent.putExtra("model_url", model.getModelUrl());
        intent.putExtra("latitud", model.getLatitud());
        intent.putExtra("longitud", model.getLongitud());
        if (model.getSlides() != null) {
            intent.putStringArrayListExtra("slides", new ArrayList<>(model.getSlides()));
        }
        intent.putExtra("slides_url", model.getSlidesUrl());
        startActivity(intent);
    }
}

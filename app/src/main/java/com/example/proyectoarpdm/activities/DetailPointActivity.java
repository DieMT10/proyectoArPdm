package com.example.proyectoarpdm.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.fragment.app.FragmentActivity;

import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.helloar.HelloArActivity;

public class DetailPointActivity extends FragmentActivity {

    private Button btnIniciarAR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_detail_point);

        btnIniciarAR = findViewById(R.id.btnIniciarAR);

        btnIniciarAR.setOnClickListener(v -> {

            Intent intent = new Intent(
                    DetailPointActivity.this,
                    HelloArActivity.class
            );

            startActivity(intent);

        });
    }
}
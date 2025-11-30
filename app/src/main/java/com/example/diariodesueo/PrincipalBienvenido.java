package com.example.diariodesueo;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public class PrincipalBienvenido extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.principal);

        };
    public void empezarcambio(View v){
        Intent intent = new Intent(this, FuncionamientoDiario.class);
        startActivity(intent);
    }
    public void pregunta(View view){
        Toast.makeText(this, "Lo que hace esta aplicación es que Monitorea luz, ruido y horas de sueño para descansar mejor.", Toast.LENGTH_SHORT).show();
    }
    }


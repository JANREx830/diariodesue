package com.example.diariodesueo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;

public class FuncionamientoDiario extends AppCompatActivity implements SensorEventListener {

    // Variables de UI (Coinciden con tus IDs del XML)
    private TextView tvTimer, tvLightSensor, tvNoiseSensor, tvRecomendacion;
    private Button btnSleepToggle;

    // Variables de Lógica
    private boolean isTracking = false;
    private long startTime = 0;

    // Variables para Sensores
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private MediaRecorder mediaRecorder;

    // Handler para actualizar UI (Timer y Ruido) cada segundo
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private static final int REQUEST_AUDIO_PERMISSION_CODE = 1;

    // Variables para estadísticas
    private double maxNoise = 0;
    private float maxLight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.funcionamiento); // Asegúrate que este sea el nombre de tu segundo XML

        // 1. Vincular vistas
        tvTimer = findViewById(R.id.tvTimer);
        tvLightSensor = findViewById(R.id.tvLightSensor);
        tvNoiseSensor = findViewById(R.id.tvNoiseSensor);
        tvRecomendacion = findViewById(R.id.tv_ruido); // ID del TextView de recomendación
        btnSleepToggle = findViewById(R.id.btnSleepToggle);

        // 2. Inicializar Sensor de Luz
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
    }

    // --- MTODO VINCULADO AL BOTÓN (android:onClick="regsue") ---
    public void regsue(View view) {
        if (!isTracking) {
            // Intentar iniciar
            if (checkPermissions()) {
                startMonitoring();
            } else {
                requestPermissions();
            }
        } else {
            // Detener
            stopMonitoring();
        }
    }

    private void startMonitoring() {
        isTracking = true;
        startTime = System.currentTimeMillis();
        btnSleepToggle.setText("Detener Sueño");
        btnSleepToggle.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));

        // Iniciar Sensor de Luz
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            tvLightSensor.setText("N/A");
        }

        // Iniciar Grabadora para Ruido
        setupMediaRecorder();

        // Iniciar Loop del Timer
        timerHandler.postDelayed(timerRunnable, 0);
    }

    private void stopMonitoring() {
        isTracking = false;
        btnSleepToggle.setText("Registrar Sueño");
        btnSleepToggle.setBackgroundColor(0xFF673AB7); // Tu color original morado

        // Detener sensores
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopMediaRecorder();
        timerHandler.removeCallbacks(timerRunnable);

        // Generar recomendación final
        generarRecomendacion();
    }

    // --- LÓGICA DEL TIMER Y RUIDO (Se ejecuta repetidamente) ---
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            int hours = minutes / 60;
            seconds = seconds % 60;
            minutes = minutes % 60;

            // Actualizar Texto del Timer
            tvTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

            // Actualizar Ruido (dB)
            if (mediaRecorder != null) {
                double amplitude = mediaRecorder.getMaxAmplitude();
                if (amplitude > 0) {
                    // Fórmula para convertir amplitud a Decibelios
                    double db = 20 * Math.log10(amplitude);
                    tvNoiseSensor.setText(String.format("%.1f dB", db));

                    if(db > maxNoise) maxNoise = db; // Guardar máximo para recomendación
                }
            }

            // Ejecutar de nuevo en 500ms (medio segundo)
            if (isTracking) {
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    // --- LÓGICA DEL SENSOR DE LUZ (Se ejecuta cuando cambia la luz) ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            tvLightSensor.setText(String.format("%.0f lx", lux));

            if(lux > maxLight) maxLight = lux; // Guardar máximo
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No necesario para este proyecto
    }

    // --- LÓGICA DEL MICRÓFONO (MediaRecorder) ---
    private void setupMediaRecorder() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
            try {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setOutputFile("/dev/null"); // Truco: No guardar archivo, solo escuchar
                mediaRecorder.prepare();
                mediaRecorder.start();
            } catch (IOException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // --- LÓGICA DE RECOMENDACIONES ---
    private void generarRecomendacion() {
        String recomendacion = "";

        if (maxNoise > 70) {
            recomendacion += "Hubo picos de ruido altos (>70dB). Considera usar tapones. ";
        } else {
            recomendacion += "Nivel de ruido adecuado. ";
        }

        if (maxLight > 50) {
            recomendacion += "\nHubo mucha luz (" + maxLight + " lux). Intenta usar un antifaz.";
        } else {
            recomendacion += "\nIluminación correcta para descansar.";
        }

        tvRecomendacion.setText(recomendacion);
    }

    // --- GESTIÓN DE PERMISOS ---
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso concedido, pulsa Empezar de nuevo", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Se necesita permiso de audio para medir ruido", Toast.LENGTH_LONG).show();
            }
        }
    }
}
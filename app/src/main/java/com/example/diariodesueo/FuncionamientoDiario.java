package com.example.diariodesueo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// --- IMPORTACIONES DE LA GRÁFICA (MPAndroidChart) ---
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;

public class FuncionamientoDiario extends AppCompatActivity implements SensorEventListener {

    // UI
    private TextView tvTimer, tvLightSensor, tvNoiseSensor, tvRecomendacion;
    private Button btnSleepToggle;
    private LineChart mChart; // La gráfica

    // Lógica
    private boolean isTracking = false;
    private long startTime = 0;

    // Sensores
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private MediaRecorder mediaRecorder;

    // Handler para el Loop (Timer y Gráfica)
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private static final int REQUEST_AUDIO_PERMISSION_CODE = 1;

    // Estadísticas
    private double maxNoise = 0;
    private float maxLight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.funcionamiento);

        // 1. Vincular IDs (Asegúrate que coinciden con tu XML)
        tvTimer = findViewById(R.id.tvTimer);
        tvLightSensor = findViewById(R.id.tvLightSensor);
        tvNoiseSensor = findViewById(R.id.tvNoiseSensor);
        tvRecomendacion = findViewById(R.id.tv_ruido);
        btnSleepToggle = findViewById(R.id.btnSleepToggle);
        mChart = findViewById(R.id.lineChart); // ID de la gráfica en el XML

        // 2. Configurar la Gráfica (Estilo visual)
        setupChart();

        // 3. Inicializar Sensor de Luz (El de ruido se inicia al grabar)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
    }

    // --- CONFIGURACIÓN ESTILO DE LA GRÁFICA (JAVA) ---
    private void setupChart() {
        mChart.getDescription().setEnabled(false);
        mChart.setTouchEnabled(false);
        mChart.setDragEnabled(false);
        mChart.setScaleEnabled(false);
        mChart.setDrawGridBackground(false);
        mChart.setPinchZoom(false);
        mChart.setBackgroundColor(Color.TRANSPARENT);

        // Datos vacíos al inicio
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        mChart.setData(data);

        // Eje X (Abajo)
        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(false);

        // Eje Y (Izquierda)
        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#40FFFFFF")); // Rejilla tenue

        // Eje Y (Derecha) - Desactivado
        mChart.getAxisRight().setEnabled(false);
    }

    // --- AGREGAR DATOS A LA GRÁFICA EN TIEMPO REAL ---
    private void addEntryToChart(float valorRuido) {
        LineData data = mChart.getData();

        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);

            // Si es el primer dato, creamos la línea
            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            // Agregamos el nuevo valor (Eje X = contador, Eje Y = Ruido)
            data.addEntry(new Entry(set.getEntryCount(), valorRuido), 0);
            data.notifyDataChanged();

            // Limitamos a ver solo los últimos 50 puntos para dar efecto de movimiento
            mChart.notifyDataSetChanged();
            mChart.setVisibleXRangeMaximum(50);
            mChart.moveViewToX(data.getEntryCount());
        }
    }

    // Crear el estilo de la línea (Color cian, grosor, etc.)
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Ruido (dB)");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.CYAN);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Línea curva suave
        return set;
    }

    // --- ACCIÓN DEL BOTÓN ---
    public void regsue(View view) {
        if (!isTracking) {
            if (checkPermissions()) {
                startMonitoring();
            } else {
                requestPermissions();
            }
        } else {
            stopMonitoring();
        }
    }

    private void startMonitoring() {
        isTracking = true;
        startTime = System.currentTimeMillis();

        // Cambio visual del botón
        btnSleepToggle.setText("DETENER SUEÑO");
        btnSleepToggle.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));

        // Limpiar gráfica anterior
        if (mChart.getData() != null) {
            mChart.getData().clearValues();
            mChart.notifyDataSetChanged();
            mChart.clear();
            setupChart(); // Reiniciamos ejes
        }

        // Activar Sensor Luz
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Activar Micrófono
        setupMediaRecorder();

        // Iniciar Loop (Cronómetro + Actualización Gráfica)
        timerHandler.postDelayed(timerRunnable, 0);
    }

    private void stopMonitoring() {
        isTracking = false;

        // Cambio visual del botón
        btnSleepToggle.setText("REGISTRAR SUEÑO");
        btnSleepToggle.setBackgroundColor(getResources().getColor(com.google.android.material.R.color.design_default_color_primary));

        // Detener todo
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopMediaRecorder();
        timerHandler.removeCallbacks(timerRunnable);

        generarRecomendacion();
    }

    // --- LOOP PRINCIPAL (SE EJECUTA CADA 0.5 SEGUNDOS) ---
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            // 1. Actualizar Timer
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            int hours = minutes / 60;
            seconds = seconds % 60;
            minutes = minutes % 60;
            tvTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

            // 2. Actualizar Ruido y Gráfica
            if (mediaRecorder != null) {
                double amplitude = mediaRecorder.getMaxAmplitude();
                if (amplitude > 0) {
                    // Convertir amplitud a decibelios
                    double db = 20 * Math.log10(amplitude);

                    // Mostrar texto
                    tvNoiseSensor.setText(String.format("%.1f dB", db));

                    // Actualizar gráfica
                    addEntryToChart((float) db);

                    if (db > maxNoise) maxNoise = db;
                }
            }

            // Repetir
            if (isTracking) {
                timerHandler.postDelayed(this, 500);
            }
        }
    };

    // --- SENSOR LUZ ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            tvLightSensor.setText(String.format("%.0f Lux", lux));
            if (lux > maxLight) maxLight = lux;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // --- MICRÓFONO ---
    private void setupMediaRecorder() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
            try {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                // Archivo temporal seguro
                mediaRecorder.setOutputFile(getExternalCacheDir().getAbsolutePath() + "/temp_audio.3gp");
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

    // --- RECOMENDACIONES ---
    private void generarRecomendacion() {
        String rec = "";
        if (maxNoise > 60) {
            rec += "Ambiente ruidoso detected (" + (int)maxNoise + "dB). Usa tapones. ";
        } else {
            rec += "Silencio adecuado. ";
        }
        if (maxLight > 20) {
            rec += "\nMucha luz (" + (int)maxLight + " Lux). Apaga luces o usa antifaz.";
        } else {
            rec += "\nOscuridad perfecta.";
        }
        tvRecomendacion.setText(rec);
    }

    // --- PERMISOS ---
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso concedido. Intenta de nuevo.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Sin micrófono no podemos medir el ruido.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
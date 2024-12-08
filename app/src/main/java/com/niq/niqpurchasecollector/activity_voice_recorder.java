package com.niq.niqpurchasecollector;

import android.Manifest;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class activity_voice_recorder extends AppCompatActivity {

    private TextView tvRecordingStatus;
    private ProgressBar progressBar;
    private Button btnSend, btnDiscard;
    private SpeechRecognizer speechRecognizer;
    private MediaRecorder mediaRecorder;
    private String recordedText = "";
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_recorder);

        tvRecordingStatus = findViewById(R.id.tvRecordingStatus);
        progressBar = findViewById(R.id.progressBar);
        btnSend = findViewById(R.id.btnSend);
        btnDiscard = findViewById(R.id.btnDiscard);

        setupSpeechRecognizer();

        btnSend.setOnClickListener(v -> saveToFile());
        btnDiscard.setOnClickListener(v -> finish());
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvRecordingStatus.setText("Hablando...");
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onBeginningOfSpeech() {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onEndOfSpeech() {
                progressBar.setVisibility(View.GONE);
                tvRecordingStatus.setText("Procesando...");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    recordedText = matches.get(0);
                    tvRecordingStatus.setText("Grabación completada");
                    btnSend.setEnabled(true);
                }
            }

            @Override
            public void onError(int error) {
                tvRecordingStatus.setText("Error en la grabación. Intenta de nuevo.");
            }

            // Métodos no usados
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
        });
    }

    private void startRecording() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        speechRecognizer.startListening(intent);
    }

    private void saveToFile() {
        File directory = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/archivos_recibidos");
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "No se pudo crear el directorio", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(directory, "grabacion_" + System.currentTimeMillis() + ".txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(recordedText);
            Toast.makeText(this, "Archivo guardado en " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al guardar el archivo", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}

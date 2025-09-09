package com.niq.niqpurchasecollector;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.*;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class activity_voice_recorder extends AppCompatActivity {

    private TextView tvTimer, tvRecordingStatus;
    private ImageButton btnRecord, btnPlay;
    private Button btnSend, btnDiscard, btnBackToMenu;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private File recordedFile, tempFile;
    private boolean isRecording = false, isPaused = false;
    private long startTime = 0, pauseTime = 0;
    private Handler timerHandler = new Handler();
    private long totalRecordedTime = 0;
    private long pauseDuration = 0;
    private String smsId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_recorder);

        tvRecordingStatus = findViewById(R.id.tvRecordingStatus);
        tvTimer = findViewById(R.id.tvTimer);
        btnRecord = findViewById(R.id.btnRecord);
        btnPlay = findViewById(R.id.btnPlay);
        btnSend = findViewById(R.id.btnSend);
        btnDiscard = findViewById(R.id.btnDiscard);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);

        btnSend.setVisibility(View.GONE);
        btnDiscard.setVisibility(View.GONE);
        btnPlay.setVisibility(View.GONE);

        btnRecord.setOnClickListener(v -> toggleRecording());
        btnPlay.setOnClickListener(v -> playRecording());
        btnSend.setOnClickListener(v -> saveRecording());
        btnDiscard.setOnClickListener(v -> discardRecording());
        btnBackToMenu.setOnClickListener(v -> exitRecording());

        SharedPreferences sharedPreferences = getSharedPreferences("ConfigTienda", MODE_PRIVATE);
        smsId = sharedPreferences.getString("smsid", "");

    }

    private void exitRecording() {
        finish();
    }

    private void toggleRecording() {
        if (isRecording) {
            pauseRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            if (isRecording) return;

            btnPlay.setVisibility(View.INVISIBLE);

            // 🔥 Asegurar que los archivos temporales se eliminan antes de iniciar
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }

            tempFile = new File(getCacheDir(), "temp_recording_" + System.currentTimeMillis() + ".mp3");

            // 🔥 Liberar MediaRecorder antes de iniciar una nueva grabación
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }

            // Inicializar MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(tempFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            isPaused = false;
            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(updateTimerRunnable, 1000);

            btnRecord.setImageResource(android.R.drawable.ic_media_pause);
            tvRecordingStatus.setText("Grabando...");
        } catch (IOException e) {
            Toast.makeText(this, "Error al iniciar grabación", Toast.LENGTH_SHORT).show();
        }
    }


    private void pauseRecording() {
        if (!isRecording || isPaused) return;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            isPaused = true;
            timerHandler.removeCallbacks(updateTimerRunnable);

            totalRecordedTime += System.currentTimeMillis() - startTime;

            // 🔥 Fusionar la grabación previa con la nueva
            if (recordedFile != null && recordedFile.exists()) {
                File mergedFile = new File(getCacheDir(), "merged_" + System.currentTimeMillis() + ".mp3");
                mergeAudioFiles(recordedFile, tempFile, mergedFile);
                recordedFile.delete(); // Eliminar el archivo antiguo
                recordedFile = mergedFile;
            } else {
                recordedFile = tempFile;
            }

            btnRecord.setImageResource(R.drawable.ic_mic);
            tvRecordingStatus.setText("Grabación pausada");
            btnPlay.setVisibility(View.VISIBLE);
            btnSend.setVisibility(View.VISIBLE);
            btnDiscard.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, "Error al pausar grabación", Toast.LENGTH_SHORT).show();
        }
    }


    private void playRecording() {
        if (recordedFile == null || !recordedFile.exists()) {
            //Toast.makeText(this, "Error: Archivo no encontrado", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Reiniciar MediaPlayer cada vez
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(recordedFile.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> btnPlay.setImageResource(android.R.drawable.ic_media_play));

            mediaPlayer.start();
            btnPlay.setImageResource(android.R.drawable.ic_media_pause);
        } catch (IOException e) {
            //Toast.makeText(this, "Error al reproducir", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveRecording() {
        if (recordedFile == null || !recordedFile.exists()) {
            return;
        }

        // 📂 Ruta de destino
        File storageDir = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

        // 🛠️ Crear la carpeta si no existe
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            //Toast.makeText(this, "Error al crear carpeta", Toast.LENGTH_SHORT).show();
            return;
        }

        // Crear el archivo final con timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
        File finalFile = new File(storageDir, smsId + "_" + timeStamp + ".mp3");
        try {
            // Copiar archivo en lugar de usar renameTo()
            copyFile(recordedFile, finalFile);
            executeFirebaseNow();
            finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void discardRecording() {
        if (recordedFile != null && recordedFile.exists()) {
            recordedFile.delete();
        }
        btnPlay.setVisibility(View.INVISIBLE);
        btnDiscard.setVisibility(View.INVISIBLE);
        btnSend.setVisibility(View.INVISIBLE);
        tvRecordingStatus.setText("Toque el boton grabar para comenzar");
        finish();
    }

    private void mergeAudioFiles(File input1, File input2, File output) throws IOException {
        if (!input1.exists()) {
            input2.renameTo(output);
            return;
        }
        if (!input2.exists()) {
            input1.renameTo(output);
            return;
        }

        MediaExtractor extractor1 = new MediaExtractor();
        MediaExtractor extractor2 = new MediaExtractor();
        MediaMuxer muxer = null;

        try {
            extractor1.setDataSource(input1.getAbsolutePath());
            extractor2.setDataSource(input2.getAbsolutePath());

            // Verificar si los archivos tienen pistas de audio antes de fusionar
            if (extractor1.getTrackCount() == 0 || extractor2.getTrackCount() == 0) {
                Log.e("MERGE_ERROR", "Uno de los archivos no tiene pistas de audio.");
                return;
            }

            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            extractor1.selectTrack(0);
            extractor2.selectTrack(0);

            MediaFormat format = extractor1.getTrackFormat(0);
            int trackIndex = muxer.addTrack(format);
            muxer.start();

            // 🔥 Escribir datos de audio asegurando que el tiempo se maneja correctamente
            writeSampleData(extractor1, muxer, trackIndex, 0);
            writeSampleData(extractor2, muxer, trackIndex, extractor1.getSampleTime());

            muxer.stop();
        } catch (Exception e) {
            Log.e("MERGE_ERROR", "Fallo en fusión: " + e.getMessage());
            input2.renameTo(output);
        } finally {
            extractor1.release();
            extractor2.release();
            if (muxer != null) {
                muxer.release();
            }
            input1.delete();
            input2.delete();
        }
    }



    private void copyFile(File sourceFile, File destFile) throws IOException {
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Archivo origen no encontrado: " + sourceFile.getAbsolutePath());
        }

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[1024 * 4]; // Buffer de 4KB
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } catch (IOException e) {
            Log.e("COPY_ERROR", "Error copiando archivo: " + e.getMessage());
            throw e; // Relanzar para manejar en mergeAudioFiles
        }
    }

    private void writeSampleData(MediaExtractor extractor, MediaMuxer muxer, int trackIndex, long timeOffset) {
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        while (extractor.readSampleData(buffer, 0) >= 0) {
            bufferInfo.offset = 0;
            bufferInfo.size = extractor.readSampleData(buffer, 0);

            if (bufferInfo.size < 0) {
                break; // No hay más datos
            }

            bufferInfo.presentationTimeUs = extractor.getSampleTime() + timeOffset;

            // 🔥 Mapeo correcto de los flags
            int sampleFlags = extractor.getSampleFlags();
            bufferInfo.flags = 0;
            if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }

            muxer.writeSampleData(trackIndex, buffer, bufferInfo);
            extractor.advance();
        }
    }


    private Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - startTime;
                int seconds = (int) (elapsed / 1000);
                tvTimer.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private void executeFirebaseNow() {
        WorkManager workManager = WorkManager.getInstance(this);
        Log.d("executeFirebaseNow()", "Ejecutando desde voice recorder");

        // Crear la tarea inmediata con el mismo tag que MainActivity
        OneTimeWorkRequest firebaseWorkRequest =
                new OneTimeWorkRequest.Builder(FirebaseWorker.class)
                        .addTag(MainActivity.FIREBASE_WORKER_TAG) // Usar el mismo tag
                        .setInitialDelay(1, TimeUnit.SECONDS)
                        .build();

        workManager.enqueueUniqueWork("FirebaseWorkerOneTime", ExistingWorkPolicy.REPLACE, firebaseWorkRequest);
        Log.d("FirebaseWorker", "Tarea de Firebase ejecutándose inmediatamente...");
    }

    private void scheduleFirebaseWorker() {
        PeriodicWorkRequest firebaseWorkRequest = new PeriodicWorkRequest.Builder(FirebaseWorker.class, 1, TimeUnit.HOURS)
                .build();
        Log.d("scheduleFirebaseWorker()", "Programando tarea Firebase");
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "FirebaseWorkerJob",
                ExistingPeriodicWorkPolicy.KEEP, // Mantiene el temporizador si ya existe
                firebaseWorkRequest
        );
    }
}

package com.niq.niqpurchasecollector;

import android.Manifest;
//import android.app.NotificationChannel;
//import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
//import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
//import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class FirebaseWorker extends Worker {

    private static final String TAG = "FirebaseWorker";
    private static final String REMOTE_PATH = BuildConfig.REMOTE_PATH; //extraer de gradle.properties

    private static final String PREFS_NAME = "UploadPrefs";
    private static final String KEY_LAST_SUCCESSFUL_UPLOAD = "last_upload_date";
    private static final String KEY_LAST_NOTIFICATION_DATE = "last_notification_date";
    private static final int INACTIVITY_DAYS_THRESHOLD = 6;

    // Constantes para los datos de progreso (igual que en MainActivity)
    public static final String KEY_PROGRESS_PERCENT = "PROGRESS_PERCENT";
    public static final String KEY_FILES_UPLOADED = "FILES_UPLOADED";
    public static final String KEY_FILES_REMAINING = "FILES_REMAINING";
    public static final String KEY_TOTAL_FILES = "TOTAL_FILES";
    public static final String KEY_ERROR_MESSAGE = "ERROR_MESSAGE";
    private long mTotalBatchSize = 0;
    private long mAccumulatedBytes = 0;
    private int mLastPercentReported = -1;
    public FirebaseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Entrando en doWork()");

        try {
            // Verifica si la carpeta existe
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), MainActivity.APP_MEDIA_PATH);
            if (!folder.exists()) {
                Log.e(TAG, "Carpeta no existe: " + folder.getAbsolutePath());
                setProgressAsync(createProgressData(0,0,0,0, "Carpeta de envío no encontrada"));
                return Result.failure(new Data.Builder().putString(KEY_ERROR_MESSAGE, "Carpeta de envío no encontrada").build());
            }

            File[] files = folder.listFiles();
            if (files == null || files.length == 0) {
                Log.d(TAG, "No hay archivos para enviar.");
                setProgressAsync(createProgressData(0,0,0,0, "No hay archivos para enviar"));
                return Result.success();
            }

            // Ordenar archivos por nombre (fecha) para mantener secuencia
            Arrays.sort(files, Comparator.comparing(File::getName));
            // Calcular tamaño total para progreso
            mTotalBatchSize = 0;
            for (File f : files) mTotalBatchSize += f.length();
            if (mTotalBatchSize == 0) mTotalBatchSize = 1; // Evitar división por cero
            mAccumulatedBytes = 0;
            mLastPercentReported = -1;

            int totalFiles = files.length;
            int filesUploadedSuccessfully = 0;
            int filesProcessed = 0; // Para contar los archivos que intentamos procesar

            // Progreso inicial
            setProgressAsync(createProgressData(0, filesUploadedSuccessfully, totalFiles - filesUploadedSuccessfully, totalFiles, "Iniciando..."));

            // Subir los archivos
            for (File file : files) {
                filesProcessed++;
                // Pasamos contexto para actualizar progreso en tiempo real
                if (uploadFile(file, filesUploadedSuccessfully, totalFiles, filesProcessed)) {
                    filesUploadedSuccessfully++;
                    mAccumulatedBytes += file.length(); // Sumar al acumulado global tras éxito
                    boolean deleted = file.delete();
                    Log.d(TAG, "Archivo " + file.getName() + (deleted ? " eliminado" : " no eliminado"));
                } else {
                    Log.e(TAG, "Error al subir: " + file.getName());
                    // Si falla, sumamos los bytes de todas formas al acumulado para no retroceder el progreso visual
                    mAccumulatedBytes += file.length();
                }
                // Calculamos porcentaje final de este paso (asegurando que cuadre con el total)
                int progressPercent = (int) ((mAccumulatedBytes * 100) / mTotalBatchSize);
                int filesRemaining = totalFiles - filesProcessed;

                Log.d(TAG, "Procesados: " + filesProcessed + "/" + totalFiles +
                        ", Subidos exitosamente: " + filesUploadedSuccessfully +
                        ", Progreso: " + progressPercent + "%");

                String statusMessage = filesProcessed < totalFiles ? "Enviando..." : "Completando...";
                setProgressAsync(createProgressData(progressPercent, filesUploadedSuccessfully, filesRemaining, totalFiles, statusMessage));
            }
            if (filesUploadedSuccessfully == totalFiles) {
                Log.d(TAG, "Todos los " + totalFiles + " archivos enviados exitosamente.");
                // Progreso final exitoso
                saveSuccessfulUploadDate();
                //checkAndShowInactivityNotification();
                setProgressAsync(createProgressData(100, filesUploadedSuccessfully, 0, totalFiles, "Completado"));
                return Result.success();
            } else if (filesUploadedSuccessfully > 0) {
                Log.w(TAG, filesUploadedSuccessfully + " de " + totalFiles + " archivos enviados. Algunos fallaron.");
                // Aún consideramos éxito parcial si algunos se subieron, el progreso lo reflejará.
                // La UI puede mostrar el estado final.
                saveSuccessfulUploadDate();
                //checkAndShowInactivityNotification();
                Data outputData = new Data.Builder().putString(KEY_ERROR_MESSAGE, "Algunos archivos no se pudieron subir.").build();
                setProgressAsync(createProgressData((filesUploadedSuccessfully * 100) / totalFiles, filesUploadedSuccessfully, totalFiles - filesUploadedSuccessfully, totalFiles, "Completado con errores"));
                return Result.success(outputData); // Éxito, pero con errores reportados
            } else {
                Log.e(TAG, "Ningún archivo pudo ser subido de " + totalFiles + " intentos.");
                Data errorData = new Data.Builder().putString(KEY_ERROR_MESSAGE, "No se pudo subir ningún archivo.").build();
                setProgressAsync(createProgressData(0, 0, totalFiles, totalFiles, "Error en todos los archivos"));
                return Result.failure(errorData);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error general en doWork: " + e.getMessage(), e);
            Data errorData = new Data.Builder().putString(KEY_ERROR_MESSAGE, "Error inesperado en el worker.").build();
            setProgressAsync(createProgressData(0,0,0,0, "Error en worker"));
            return Result.failure(errorData);
        } finally {
            checkAndShowInactivityNotification();
        }

        //return Result.success();
    }

    private boolean uploadFile(File file, int currentSuccessCount, int totalFiles, int currentFileIndex) {
        try {
            // Validación de tamaño: 15MB
            long fileSizeInBytes = file.length();
            long maxSize = 15 * 1024 * 1024; // 15 MB en bytes
            long minSize = 100; //

            if (fileSizeInBytes > maxSize) {
                Log.w(TAG, "Archivo demasiado grande (" + (fileSizeInBytes / (1024 * 1024)) + " MB). Se elimina.");

                // Borrar archivo
                if (file.delete()) {
                    Log.d(TAG, "Archivo eliminado: " + file.getName());
                } else {
                    Log.e(TAG, "No se pudo eliminar el archivo: " + file.getName());
                }

                // Aviso al usuario
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() ->
                        Toast.makeText(getApplicationContext(),
                                "No se permiten archivos mayores a 15 MB",
                                Toast.LENGTH_LONG).show()
                );

                return false;
            }else if (fileSizeInBytes < minSize) {
                // Borrar archivo
                if (file.delete()) {
                    Log.d(TAG, "Archivo eliminado: " + file.getName());
                } else {
                    Log.e(TAG, "No se pudo eliminar el archivo: " + file.getName());
                }
            }

            // 📂 Parsear nombre de archivo para obtener código de tienda y periodo
            String fileName = file.getName();
            String[] parts = fileName.split("_");

            if (parts.length < 2) {
                Log.e(TAG, "Nombre de archivo inválido: " + fileName);
                // Eliminar archivo inválido para evitar reintentos infinitos
                if (file.delete()) {
                    Log.d(TAG, "Archivo con nombre inválido eliminado: " + fileName);
                }
                return false;
            }

            String codigoTienda = parts[0];             // Ej: 5700088888
            String fechaCompleta = parts[1];            // Ej: 20250826
            String periodo = fechaCompleta.substring(0, 6); // Ej: 202508

            // Construir ruta en Firebase
            String remoteFilePath = REMOTE_PATH + codigoTienda + "/" + periodo + "/" + fileName;

            StorageReference storageRef = FirebaseStorage
                    .getInstance("gs://niq-purchase-collector-9723c.firebasestorage.app")
                    .getReference()
                    .child(remoteFilePath);

            Log.d(TAG, "Intentando subir a: " + storageRef.getPath());
            Log.d(TAG, "Bucket: " + storageRef.getBucket());

            // Subida
            UploadTask uploadTask = storageRef.putFile(Uri.fromFile(file));

            // Listener para progreso en tiempo real (byte a byte)
            uploadTask.addOnProgressListener(snapshot -> {
                long bytesTransferredThisFile = snapshot.getBytesTransferred();
                long currentGlobalBytes = mAccumulatedBytes + bytesTransferredThisFile;
                int percent = (int) ((currentGlobalBytes * 100) / mTotalBatchSize);

                // Actualizar solo si el porcentaje cambia
                if (percent > mLastPercentReported) {
                    mLastPercentReported = percent;
                    String status = "Enviando archivo " + currentFileIndex + " de " + totalFiles + "...";
                    setProgressAsync(createProgressData(percent, currentSuccessCount, totalFiles - currentSuccessCount, totalFiles, status));
                }
            });

            // Bloquear hasta que finalice
            Tasks.await(uploadTask);

            if (uploadTask.isSuccessful()) {
                Log.d(TAG, "Subir " + fileName + ": Éxito en carpeta " + codigoTienda + "/" + periodo);
                return true;
            } else {
                Log.e(TAG, "Error al subir " + fileName);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error al subir " + file.getName() + ": " + e.getMessage(), e);
            return false;
        }
    }
    private Data createProgressData(int percent, int uploaded, int remaining, int total, String statusMessage) {
        return new Data.Builder()
                .putInt(KEY_PROGRESS_PERCENT, percent)
                .putInt(KEY_FILES_UPLOADED, uploaded)
                .putInt(KEY_FILES_REMAINING, remaining)
                .putInt(KEY_TOTAL_FILES, total)
                .putString("STATUS_MESSAGE", statusMessage) // Mensaje de estado adicional
                .build();
    }

    private void saveSuccessfulUploadDate() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_LAST_SUCCESSFUL_UPLOAD, System.currentTimeMillis())
                .apply();
    }

    private void checkAndShowInactivityNotification() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpload = prefs.getLong(KEY_LAST_SUCCESSFUL_UPLOAD, 0);
        long lastNotification = prefs.getLong(KEY_LAST_NOTIFICATION_DATE, 0);
        long currentTime = System.currentTimeMillis();

        // Cálculo de días
        long daysSinceLastUpload = TimeUnit.MILLISECONDS.toDays(currentTime - lastUpload);
        long daysSinceLastNotification = TimeUnit.MILLISECONDS.toDays(currentTime - lastNotification);

        // Usar umbral configurado (6 días)
        if (daysSinceLastUpload >= INACTIVITY_DAYS_THRESHOLD &&
                daysSinceLastNotification >= 1) { // Notificar si pasó un día desde la última notificación para no spamear

             // Si nunca se ha notificado (lastNotification == 0), daysSinceLastNotification será enorme, así que entra.
             // Pero si acabamos de notificar, esperamos al menos 1 día para la siguiente.

             // Corrección lógica:
             // Queremos notificar si pasaron > 6 días desde carga
             // Y si no hemos notificado hoy.
             
             if (currentTime - lastNotification > TimeUnit.DAYS.toMillis(1)) {
                 showInactivityNotification();
                 prefs.edit()
                        .putLong(KEY_LAST_NOTIFICATION_DATE, currentTime)
                        .apply();
                 Log.d(TAG, "Notificación de inactividad mostrada.");
             }
        }
    }

    private void showInactivityNotification() {
        Context context = getApplicationContext();

        // Verificar permiso de notificaciones para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No se tiene permiso para mostrar notificaciones");
                return;
            }
        }

        // Crear intent para abrir la app al hacer clic
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        // Crear notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "channel_id")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Ícono temporal del sistema
                .setContentTitle("Recordatorio de compras")
                .setContentText("Notificación Nielsen: Si ha realizado compras y no le han dado facturas por favor ayudenos subiendo la información.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        try {
            notificationManager.notify(1, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Error de seguridad al mostrar notificación: " + e.getMessage());
        }
    }
}

package com.niq.niqpurchasecollector;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;

public class FirebaseWorker extends Worker {

    private static final String TAG = "FirebaseWorker";
    private static final String REMOTE_PATH = BuildConfig.REMOTE_PATH; //extraer de gradle.properties

    public FirebaseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Entrando en doWork()");

        try {
            // Verifica si la carpeta existe
            File folder = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");
            if (!folder.exists()) {
                Log.e(TAG, "Carpeta no existe: " + folder.getAbsolutePath());
                return Result.failure();
            }

            File[] files = folder.listFiles();
            if (files == null || files.length == 0) {
                Log.d(TAG, "No hay archivos para enviar.");
                return Result.success();
            }

            // Subir los archivos
            for (File file : files) {
                if (uploadFile(file)) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "Archivo " + file.getName() + (deleted ? " eliminado" : " no eliminado"));
                } else {
                    Log.e(TAG, "Error al subir: " + file.getName());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error general: " + e.getMessage(), e);
            return Result.failure();
        }

        return Result.success();
    }

    private boolean uploadFile(File file) {
        try {
            // Validación de tamaño: 15MB
            long fileSizeInBytes = file.length();
            long maxSize = 15 * 1024 * 1024; // 15 MB en bytes

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
            }

            // 📂 Parsear nombre de archivo para obtener código de tienda y periodo
            String fileName = file.getName();
            String[] parts = fileName.split("_");

            if (parts.length < 2) {
                Log.e(TAG, "Nombre de archivo inválido: " + fileName);
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
}
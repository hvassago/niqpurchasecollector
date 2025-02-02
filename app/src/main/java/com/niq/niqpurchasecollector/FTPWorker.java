package com.niq.niqpurchasecollector;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FTPWorker extends Worker {

    private static final String TAG = "FTPWorker";
    private static final String FTP_HOST = "ftp.drivehq.com";
    private static final String FTP_USER = "simonese";
    private static final String FTP_PASS = "Darius96005744";
    private static final String REMOTE_PATH = "/drivehqshare/tttheenry/GroupWrite/";

    // 🔥 Control de ejecución
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    public FTPWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 🔥 Si ya se está ejecutando, salirse inmediatamente
        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Tarea ya en ejecución, saliendo...");
            return Result.success();
        }

        try {
            File folder = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");
            if (!folder.exists() || !folder.isDirectory()) {
                Log.e(TAG, "La carpeta no existe o no es un directorio.");
                return Result.failure();
            }

            File[] files = folder.listFiles();
            if (files == null || files.length == 0) {
                Log.d(TAG, "No hay archivos para enviar.");
                return Result.success();
            }

            FTPClient ftpClient = new FTPClient();

            try {
                // 🔹 Conectar al servidor FTP
                ftpClient.connect(FTP_HOST);
                if (!ftpClient.login(FTP_USER, FTP_PASS)) {
                    Log.e(TAG, "Error en login FTP.");
                    return Result.failure();
                }

                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                for (File file : files) {
                    if (uploadFile(ftpClient, file)) {
                        file.delete(); // ✅ Eliminar el archivo si se subió correctamente
                        Log.d(TAG, "Archivo enviado y eliminado: " + file.getName());
                    } else {
                        Log.e(TAG, "Error al enviar: " + file.getName());
                    }
                }

                ftpClient.logout();
                ftpClient.disconnect();
                return Result.success();

            } catch (IOException e) {
                Log.e(TAG, "Error FTP: " + e.getMessage(), e);
                return Result.failure();
            }

        } finally {
            // 🔥 Marcar la tarea como finalizada
            isRunning.set(false);
        }
    }

    private boolean uploadFile(FTPClient ftpClient, File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return ftpClient.storeFile(REMOTE_PATH + file.getName(), fis);
        } catch (IOException e) {
            Log.e(TAG, "Error al subir archivo: " + file.getName(), e);
            return false;
        }
    }
}

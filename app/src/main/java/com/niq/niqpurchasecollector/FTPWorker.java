// FTPWorker.java actualizado
package com.niq.niqpurchasecollector;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.apache.commons.net.ftp.FTPSClient; // Cambiado a FTPSClient

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FTPWorker extends Worker {

    private static final String TAG = "FTPWorker";
    private static final String FTP_HOST = "ftp.drivehq.com";
    private static final String FTP_USER = "simonese";
    private static final String FTP_PASS = "Darius96005744";
    private static final String REMOTE_PATH = "/drivehqshare/tttheenry/GroupWrite/";

    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    public FTPWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        WorkManager workManager = WorkManager.getInstance(getApplicationContext());

        try {
            // Verificar si ya hay otro Worker en ejecución
            List<WorkInfo> workInfos = workManager.getWorkInfosForUniqueWork("FTPWorkerOneTime").get();
            for (WorkInfo workInfo : workInfos) {
                if (workInfo.getState() == WorkInfo.State.RUNNING) {
                    Log.d(TAG, "Tarea ya en ejecución. Cancelando nueva instancia.");
                    return Result.success();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error verificando estado del Worker", e);
        }

        // Control de concurrencia con AtomicBoolean
        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Tarea ya en ejecución.");
            return Result.success();
        }

        try {
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

            FTPSClient ftpClient = new FTPSClient();
            try {
                // Conectar y configurar FTPS
                ftpClient.connect(FTP_HOST, 21);
                Log.d(TAG, "Respuesta connect: " + ftpClient.getReplyString());

                if (!ftpClient.login(FTP_USER, FTP_PASS)) {
                    Log.e(TAG, "Login fallido: " + ftpClient.getReplyString());
                    return Result.failure();
                }
                Log.d(TAG, "Login exitoso: " + ftpClient.getReplyString());

                // Configurar cifrado
                ftpClient.execPROT("P");
                ftpClient.execPBSZ(0);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);

                for (File file : files) {
                    if (uploadFile(ftpClient, file)) {
                        boolean deleted = file.delete();
                        Log.d(TAG, "Archivo " + file.getName() + (deleted ? " eliminado" : " no eliminado"));
                    } else {
                        Log.e(TAG, "Error al subir: " + file.getName());
                    }
                }

                ftpClient.logout();
            } catch (IOException e) {
                Log.e(TAG, "Error FTP: " + e.getMessage(), e);
                return Result.failure();
            } finally {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    Log.e(TAG, "Error al desconectar: " + e.getMessage());
                }
            }
            return Result.success();
        } finally {
            isRunning.set(false);
        }
    }


    private boolean uploadFile(FTPSClient ftpClient, File file) { // Cambiado a FTPSClient
        try (FileInputStream fis = new FileInputStream(file)) {
            boolean success = ftpClient.storeFile(REMOTE_PATH + file.getName(), fis);
            Log.d(TAG, "Subir " + file.getName() + ": " + (success ? "Éxito" : "Falló"));
            return success;
        } catch (IOException e) {
            Log.e(TAG, "Error al subir " + file.getName(), e);
            return false;
        }
    }
}
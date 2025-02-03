package com.niq.niqpurchasecollector;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient; // Cambiado a FTPSClient

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FTPWorker extends Worker {

    private static final String TAG = "FTPWorker";
    private static final String FTP_HOST = BuildConfig.FTP_HOST;//extraer de gradle.properties
    private static final String FTP_USER = BuildConfig.FTP_USER;//extraer de gradle.properties
    private static final String FTP_PASS = BuildConfig.FTP_PASS;//extraer de gradle.properties
    private static final String REMOTE_PATH = BuildConfig.REMOTE_PATH;//extraer de gradle.properties
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    public FTPWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Entrando en doWork()");

        FTPClient ftpClient = null;

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

            boolean useFTPS = true; // Intentar primero con FTPS

            try {
                ftpClient = new FTPSClient();
                ftpClient.connect(FTP_HOST, 21);
                Log.d(TAG, "Respuesta connect (FTPS): " + ftpClient.getReplyString());

                if (!ftpClient.login(FTP_USER, FTP_PASS)) {
                    Log.e(TAG, "Login fallido (FTPS): " + ftpClient.getReplyString());
                    return Result.failure();
                }
                Log.d(TAG, "Login exitoso (FTPS): " + ftpClient.getReplyString());

                // Configurar cifrado si es FTPS
                ((FTPSClient) ftpClient).execPROT("P");
                ((FTPSClient) ftpClient).execPBSZ(0);

            } catch (IOException e) {
                Log.w(TAG, "El servidor no admite FTPS. Probando con FTP no seguro.");
                useFTPS = false;
                ftpClient = new FTPClient();
                ftpClient.connect(FTP_HOST, 21);
                Log.d(TAG, "Respuesta connect (FTP): " + ftpClient.getReplyString());

                if (!ftpClient.login(FTP_USER, FTP_PASS)) {
                    Log.e(TAG, "Login fallido (FTP): " + ftpClient.getReplyString());
                    return Result.failure();
                }
                Log.d(TAG, "Login exitoso (FTP): " + ftpClient.getReplyString());
            }

            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            // Subir los archivos
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
                if (ftpClient != null) ftpClient.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "Error al desconectar: " + e.getMessage());
            }
        }
        return Result.success();
    }

    private boolean uploadFile(FTPClient ftpClient, File file) {
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
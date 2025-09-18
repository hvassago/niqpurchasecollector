package com.niq.niqpurchasecollector;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Menu;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.niq.niqpurchasecollector.databinding.ActivityMainBinding;

import android.Manifest;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.content.Intent;
import android.provider.Settings;
import android.provider.MediaStore;
import android.widget.RelativeLayout;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.FileInputStream;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer; // Asegúrate que es el de androidx.lifecycle
import androidx.work.Data;          // Para acceder a los datos de progreso y salida
import java.util.List;          // Para la lista de WorkInfo

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private static final int REQUEST_CODE_ALL_FILES_ACCESS = 1001;
    private static final int REQUEST_CODE_CAMERA = 2001;
    private Uri photoUri;
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final int REQUEST_CODE_GALLERY = 3001;

    private MediaRecorder mediaRecorder;
    private static final int REQUEST_CODE_FILE_PICKER = 4001;
    private String audioFilePath;
    private String smsId;
    private String telNumber;

    //private boolean isFTPWorkerScheduled = false;
    private boolean isFirebaseWorkerScheduled = false;
    private View progressContainer; // O private ConstraintLayout progressContainer;
    private ProgressBar progressBarFooter;
    private TextView progressTextFooter;
    private TextView filesStatusTextFooter;
    public static final String FIREBASE_WORKER_TAG = "FirebaseWorkerJobTag";

    @SuppressLint("QueryPermissionsNeeded")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
            );
            // Configurar token de depuración
            System.setProperty("firebase.appcheck.debug.token", "00D1EBCC-6A3C-4FE9-AB85-5A472A4BE7A2");
        } else {
            // Para producción usar Play Integrity
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "channel_id",
                    "Recordatorios",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        initializeApp();
        // Continuar después de verificar permisos
        //if (binding == null) { // Asegurarse que initializeApp (que llama a setContentView) se ha ejecutado
        //    initializeApp();
        //}

        requestAppPermissions();

        observeFirebaseWorker();
    }

    private void observeFirebaseWorker() {
        Log.d("MainActivity", "Observando FirebaseWorker con tag: " + FIREBASE_WORKER_TAG);
        WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData(FIREBASE_WORKER_TAG)
                .observe(this, new Observer<List<WorkInfo>>() {
                    @Override
                    public void onChanged(List<WorkInfo> workInfos) {
                        if (workInfos == null || workInfos.isEmpty()) {
                            if (progressContainer != null) progressContainer.setVisibility(View.GONE);
                            return;
                        }

                        // Encuentra el WorkInfo más relevante (RUNNING > ENQUEUED)
                        WorkInfo relevantWorkInfo = null;
                        for (WorkInfo workInfo : workInfos) {
                            if (workInfo.getState() == WorkInfo.State.RUNNING) {
                                relevantWorkInfo = workInfo;
                                break;
                            }
                            if (workInfo.getState() == WorkInfo.State.ENQUEUED && relevantWorkInfo == null) {
                                relevantWorkInfo = workInfo;
                                // No rompemos, seguimos buscando por si hay uno RUNNING
                            }
                        }
                        // Si no hay RUNNING o ENQUEUED, podemos tomar el último terminado para mostrar su estado final.
                        if (relevantWorkInfo == null && !workInfos.isEmpty()) {
                            relevantWorkInfo = workInfos.get(0); // El más reciente si está ordenado, o el primero.
                            // WorkManager no garantiza orden, así que esto es una heurística.
                        }


                        if (relevantWorkInfo != null) {
                            Data progressData = relevantWorkInfo.getProgress();
                            int percent = progressData.getInt(FirebaseWorker.KEY_PROGRESS_PERCENT, 0);
                            int uploaded = progressData.getInt(FirebaseWorker.KEY_FILES_UPLOADED, 0);
                            int total = progressData.getInt(FirebaseWorker.KEY_TOTAL_FILES, 0);
                            int remaining = progressData.getInt(FirebaseWorker.KEY_FILES_REMAINING, (total - uploaded)); // Usa el 'remaining' del worker si está
                            String statusMessage = progressData.getString("STATUS_MESSAGE");
                            if (statusMessage == null) statusMessage = "";


                            if (relevantWorkInfo.getState() == WorkInfo.State.RUNNING ||
                                    relevantWorkInfo.getState() == WorkInfo.State.ENQUEUED) {
                                if (progressContainer != null) progressContainer.setVisibility(View.VISIBLE);

                                if (progressBarFooter != null) progressBarFooter.setProgress(percent);
                                if (progressTextFooter != null) progressTextFooter.setText(String.format(Locale.getDefault(), "%d%%", percent));

                                if (filesStatusTextFooter != null) {
                                    if (total > 0) {
                                        filesStatusTextFooter.setText(String.format(Locale.getDefault(),
                                                "%s (%d/%d, Rest: %d)",statusMessage, uploaded, total, remaining));
                                    } else if (relevantWorkInfo.getState() == WorkInfo.State.ENQUEUED) {
                                        filesStatusTextFooter.setText("No hay envios pendientes...");
                                    } else {
                                        filesStatusTextFooter.setText(statusMessage.isEmpty() ? "Iniciando..." : statusMessage);
                                    }
                                }
                            } else if (relevantWorkInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                if (progressContainer != null) progressContainer.setVisibility(View.VISIBLE); // Mostrar estado final
                                if (progressBarFooter != null) progressBarFooter.setProgress(100);
                                if (progressTextFooter != null) progressTextFooter.setText("100%");
                                if (filesStatusTextFooter != null) {
                                    String outputMsg = relevantWorkInfo.getOutputData().getString(FirebaseWorker.KEY_ERROR_MESSAGE);
                                    if (outputMsg != null) { // Éxito pero con mensaje (ej. algunos fallaron)
                                        filesStatusTextFooter.setText("Completado: " + outputMsg + " ("+uploaded+"/"+total+")");
                                    } else {
                                        filesStatusTextFooter.setText(String.format(Locale.getDefault(), "¡Completado! %d archivos enviados.", total));
                                    }
                                }
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (progressContainer != null) progressContainer.setVisibility(View.GONE);
                                }, 5000);

                            } else if (relevantWorkInfo.getState() == WorkInfo.State.FAILED) {
                                if (progressContainer != null) progressContainer.setVisibility(View.VISIBLE); // Mostrar estado final
                                String errorMessage = relevantWorkInfo.getOutputData().getString(FirebaseWorker.KEY_ERROR_MESSAGE);
                                if (errorMessage == null) errorMessage = "Error desconocido";
                                if (filesStatusTextFooter != null) filesStatusTextFooter.setText("Fallo: " + errorMessage);
                                if (progressBarFooter != null) progressBarFooter.setProgress(percent); // Mostrar el último progreso conocido
                                if (progressTextFooter != null) progressTextFooter.setText(String.format(Locale.getDefault(), "%d%%", percent));
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (progressContainer != null) progressContainer.setVisibility(View.GONE);
                                }, 5000);
                            } else {
                                // CANCELLED, BLOCKED, o si relevantWorkInfo es un estado final y no hay más activos
                                boolean anyActive = false;
                                for(WorkInfo wi : workInfos){
                                    if(wi.getState() == WorkInfo.State.RUNNING || wi.getState() == WorkInfo.State.ENQUEUED){
                                        anyActive = true;
                                        break;
                                    }
                                }
                                if(!anyActive && progressContainer != null) {
                                    progressContainer.setVisibility(View.GONE);
                                }
                            }
                        } else {
                            // No hay ningún WorkInfo relevante (ni activo, ni el último terminado)
                            if (progressContainer != null) progressContainer.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void requestAppPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Permisos para versiones anteriores a Android 11
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // Permisos comunes
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.INTERNET);
        }

        // Permiso de notificaciones para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Permiso especial para Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_CODE_ALL_FILES_ACCESS);
            }
        }

        // Solicitar permisos normales
        if (!permissionsToRequest.isEmpty()) {
            requestPermissions(permissionsToRequest.toArray(new String[0]), REQUEST_CODE_ALL_FILES_ACCESS);
        } else {
            allPermissionsGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            allPermissionsGranted();
        } else {
            //Toast.makeText(this, "Permisos necesarios no concedidos", Toast.LENGTH_LONG).show();
        }
    }

    private void allPermissionsGranted() {
        createDirectory();  // Crear directorio si es necesario
        verifyConfig();     // Verificar configuración
        if (!isFirebaseWorkerScheduled) {
            //scheduleFTPWorker(); // Iniciar proceso FTP solo una vez
            scheduleFirebaseWorker();
            //isFTPWorkerScheduled = true; // Marcar como programado
            isFirebaseWorkerScheduled = true;
        } else {
            //executeFTPNow(); // Llamar a executeFTPNow() después de la primera ejecución
            executeFirebaseNow();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            // Crear archivo donde se guardará la imagen
            File photoFile = createImageFile();
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(cameraIntent, REQUEST_CODE_CAMERA);
            }
        } else {
            Toast.makeText(this, "No se encontró aplicación de cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Permitir múltiples selecciones
        startActivityForResult(Intent.createChooser(galleryIntent, "Selecciona imágenes"), REQUEST_CODE_GALLERY);
    }

    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            String imageFileName = smsId + "_" + timeStamp + "_";
            File storageDir = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

            // Crear directorio si no existe
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e("Directorio", "Error al crear directorio: " + storageDir.getAbsolutePath());
                return null;
            }

            File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
            return imageFile;
        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(this, "Error al crear archivo de imagen", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void startRecording() {
        Intent screenRecorderIntent = new Intent(MainActivity.this, activity_voice_recorder.class);
        startActivity(screenRecorderIntent);
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            //Toast.makeText(this, "Grabación guardada en: " + audioFilePath, Toast.LENGTH_SHORT).show();
            Log.d("ArchivoAudio", "Archivo guardado en: " + audioFilePath);
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void abrirCamara() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void createDirectory() {
        // Ruta a Android/media/com.niq.niqpurchasecollector
        File directory = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

        if (!directory.exists()) {
            if (directory.mkdirs()) {
                Log.d("Directorio", "Carpeta creada exitosamente: " + directory.getAbsolutePath());
            } else {
                Log.e("Directorio", "Error al crear la carpeta: " + directory.getAbsolutePath());
                //Toast.makeText(this, "No se pudo crear la carpeta para guardar los archivos", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d("Directorio", "La carpeta ya existe: " + directory.getAbsolutePath());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_ALL_FILES_ACCESS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                allPermissionsGranted();  // Llamamos a la función cuando se conceda el permiso
            } else {
                //Toast.makeText(this, "Permiso para acceder a todos los archivos no otorgado", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK) {
            // Imagen capturada y guardada correctamente
            if (photoUri != null) {
                //Toast.makeText(this, "Imagen guardada correctamente en: " + photoUri.getPath(), Toast.LENGTH_SHORT).show();
                Log.d("ImagenGuardada", "Imagen guardada en: " + photoUri.getPath());
            }
        } else if (requestCode == REQUEST_CODE_GALLERY && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // Múltiples imágenes seleccionadas
                int itemCount = data.getClipData().getItemCount();
                for (int i = 0; i < itemCount; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    saveImageGalleryToDirectory(imageUri, i);
                }
            } else if (data.getData() != null) {
                // Una sola imagen seleccionada
                Uri imageUri = data.getData();
                saveImageGalleryToDirectory(imageUri, 0);
            }
        } else if (requestCode == REQUEST_CODE_FILE_PICKER && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                saveFileToStorage(fileUri);
            }
        }

        // Verificar si ya se tienen todos los permisos y ejecutar verifyConfig y scheduleFTPWorker
        if (checkAllPermissionsGranted()) {
            allPermissionsGranted();
        }
    }

    private boolean checkAllPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager() &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        }
    }


    private void verifyConfig() {
        SharedPreferences sharedPreferences = getSharedPreferences("ConfigTienda", MODE_PRIVATE);

        // Verificar si la configuración existe
        if (!sharedPreferences.contains("smsid")) {
            startActivity(new Intent(this, ConfiguracionActivity.class));
            finish();
            return;
        }

        // Obtener los valores guardados
        smsId = sharedPreferences.getString("smsid", "");
        telNumber = sharedPreferences.getString("telnumber", "");
    }

    private void saveFileToStorage(Uri fileUri) {
        try {
            File storageDir = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

            // Crear la carpeta si no existe
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e("Directorio", "Error al crear directorio: " + storageDir.getAbsolutePath());
                //Toast.makeText(this, "No se pudo crear el directorio", Toast.LENGTH_SHORT).show();
                return;
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            String fileName = smsId + "_" + timeStamp + "." +
                    getFileExtension(fileUri);
            File newFile = new File(storageDir, fileName);

            // Copiar contenido del archivo seleccionado
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            OutputStream outputStream = new FileOutputStream(newFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            //Toast.makeText(this, "Archivo guardado en: " + newFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            Log.d("ArchivoGuardado", "Archivo guardado en: " + newFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(this, "Error al guardar el archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileExtension(Uri uri) {
        String extension = null;
        try {
            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null) {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            }
        } catch (Exception e) {
            Log.e("EXTENSION_ERROR", "No se pudo obtener la extensión del archivo", e);
        }
        return (extension != null) ? extension : "dat"; // Si no se encuentra la extensión, usar ".dat"
    }

    private void saveImageGalleryToDirectory(Uri imageUri, int index) {
        try {
            // Ruta específica del directorio
            File storageDir = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

            // Crear el directorio si no existe
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e("Directorio", "Error al crear directorio: " + storageDir.getAbsolutePath());
                //Toast.makeText(this, "No se pudo crear el directorio", Toast.LENGTH_SHORT).show();
                return;
            }

            // Crear un archivo con nombre único para guardar la imagen
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            String fileName = smsId + "_" + timeStamp + "_" + index + ".jpg"; // Agregar índice al nombre
            File imageFile = new File(storageDir, fileName);

            // Copiar contenido desde el URI a un archivo
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            OutputStream outputStream = new FileOutputStream(imageFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            //Toast.makeText(this, "Imagen guardada: " + imageFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            Log.d("GuardarImagen", "Imagen guardada en: " + imageFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeApp() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        progressContainer = findViewById(R.id.progressContainer);
        progressBarFooter = findViewById(R.id.progressBarUploadFooter);
        progressTextFooter = findViewById(R.id.textViewProgressPercentageFooter);
        filesStatusTextFooter = findViewById(R.id.textViewFilesStatusFooter);

        setSupportActionBar(binding.appBarMain.toolbar);
        // Manejar el Intent después de configurar la interfaz de usuario
        handleIntent();

        // Configuración de navegación
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        RelativeLayout relativeLayoutCamera = findViewById(R.id.relativeLayoutCamera);
        relativeLayoutCamera.setOnClickListener(v -> openCamera());

        RelativeLayout relativeLayoutGallery = findViewById(R.id.relativeLayoutGallery);
        relativeLayoutGallery.setOnClickListener(v -> openGallery());

        RelativeLayout relativeLayoutVoice = findViewById(R.id.relativeLayoutVoice);
        relativeLayoutVoice.setOnClickListener(v -> startRecording());

        RelativeLayout relativeLayoutOther = findViewById(R.id.relativeLayoutOther);
        relativeLayoutOther.setOnClickListener(v -> openFilePicker()); // ⬅️ Agregamos el evento aquí
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Permite seleccionar cualquier tipo de archivo
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Selecciona un archivo"), REQUEST_CODE_FILE_PICKER);
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        SharedPreferences sharedPreferences = getSharedPreferences("ConfigTienda", MODE_PRIVATE);
        smsId = sharedPreferences.getString("smsid", "");

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("image/*".equals(type) || "application/pdf".equals(type)) {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    File mediaDirectory = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

                    // Verificar y crear directorio si no existe
                    if (!mediaDirectory.exists() && !mediaDirectory.mkdirs()) {
                        Log.e("Directorio", "Error al crear la carpeta: " + mediaDirectory.getAbsolutePath());
                        //Toast.makeText(this, "No se pudo crear la carpeta para guardar los archivos", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Convertir el archivo recibido a un hash SHA-256
                    String newFileHash = getFileHash(uri);
                    if (newFileHash == null) {
                        //Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Buscar si ya existe un archivo con el mismo hash en la carpeta destino
                    if (isDuplicateFile(mediaDirectory, newFileHash)) {
                        //Toast.makeText(this, "El archivo ya existe, no se guardará duplicado", Toast.LENGTH_SHORT).show();
                        Log.w("ArchivoGuardado", "Archivo duplicado detectado, no se guardará.");
                        return;
                    }

                    // Crear nombre de archivo basado en fecha y tipo
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
                    String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(getContentResolver().getType(uri));
                    String fileName = smsId + "_" + timeStamp + "." + (extension != null ? extension : "dat");
                    File file = new File(mediaDirectory, fileName);

                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        OutputStream outputStream = new FileOutputStream(file);

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                        inputStream.close();
                        outputStream.close();

                        Toast.makeText(this, "El archivo será enviado a NIQ", Toast.LENGTH_SHORT).show();
                        Log.d("ArchivoGuardado", "Archivo guardado en: " + file.getAbsolutePath());

                    } catch (IOException e) {
                        e.printStackTrace();
                        //Toast.makeText(this, "Error al guardar el archivo", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

        // Evitar que la intención se procese varias veces
        intent.setAction(null);
    }

    private String getFileHash(Uri uri) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream inputStream = getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            inputStream.close();

            // Convertir el hash en un string hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest.digest()) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isDuplicateFile(File directory, String newFileHash) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String existingFileHash = getFileHash(Uri.fromFile(file));
                if (newFileHash.equals(existingFileHash)) {
                    return true; // Archivo duplicado encontrado
                }
            }
        }
        return false;
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);  // Actualizar el intent
        processIntent(intent);  // Procesar el nuevo intent
    }

    // Método unificado para manejar todos los intents
    private void processIntent(Intent intent) {
        if (intent == null) return;

        // Caso 1: Ejecutar FTP desde otra actividad
        if (intent.hasExtra("TRIGGER_Firebase")) {
            //executeFTPNow();
            executeFirebaseNow();
            return;
        }

        // Caso 2: Archivo compartido desde otra app
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            handleIntent();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleIntent(); // Vuelve a verificar al regresar a la app
        observeFirebaseWorker();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void scheduleFTPWorker() {
        PeriodicWorkRequest ftpWorkRequest = new PeriodicWorkRequest.Builder(FTPWorker.class, 1, TimeUnit.HOURS)
                .build();
        Log.d("scheduleFTPWorker()", "Programando tarea FTP");
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "FTPWorkerJob",
                ExistingPeriodicWorkPolicy.KEEP, // Mantiene el temporizador si ya existe
                ftpWorkRequest
        );
    }

    private void scheduleFirebaseWorker() {
        PeriodicWorkRequest firebaseWorkRequest =
                new PeriodicWorkRequest.Builder(FirebaseWorker.class, 1, TimeUnit.HOURS) // O tu intervalo
                        .addTag(FIREBASE_WORKER_TAG) // <--- AÑADIR TAG
                        .build();
        Log.d("scheduleFirebaseWorker()", "Programando tarea Firebase con tag: " + FIREBASE_WORKER_TAG);
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "FirebaseWorkerJob", // Nombre único para el trabajo periódico
                ExistingPeriodicWorkPolicy.KEEP,
                firebaseWorkRequest
        );
    }


    private void executeFTPNow() {
        WorkManager workManager = WorkManager.getInstance(this);
        Log.d("executeFTPNow()", "Ejecutando");

        // Crear la tarea inmediata con un pequeño retraso para asegurar su encolamiento
        OneTimeWorkRequest ftpWorkRequest = new OneTimeWorkRequest.Builder(FTPWorker.class)
                .setInitialDelay(1, TimeUnit.SECONDS) // Pequeño retraso para evitar problemas de encolamiento
                .build();

        workManager.enqueueUniqueWork("FTPWorkerOneTime", ExistingWorkPolicy.REPLACE, ftpWorkRequest);
        Log.d("FTPWorker", "Tarea de FTP ejecutándose inmediatamente...");

        // Reiniciar el temporizador de ejecución periódica
        scheduleFTPWorker();
    }

    private void executeFirebaseNow() {
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
            progressBarFooter.setProgress(0);
            progressTextFooter.setText("0%");
            filesStatusTextFooter.setText("Preparando envío...");
        }
        WorkManager workManager = WorkManager.getInstance(this);
        Log.d("executeFirebaseNow()", "Ejecutando");

        // Crear la tarea inmediata con un pequeño retraso para asegurar su encolamiento
        OneTimeWorkRequest firebaseWorkRequest =
                new OneTimeWorkRequest.Builder(FirebaseWorker.class)
                        .addTag(FIREBASE_WORKER_TAG) // <--- AÑADIR TAG
                        .setInitialDelay(1, TimeUnit.SECONDS) // pequeño retraso para evitar problemas de encolamiento
                        .build();

        workManager.enqueueUniqueWork("FirebaseWorkerOneTime", ExistingWorkPolicy.REPLACE, firebaseWorkRequest);
        Log.d("FirebaseWorker", "Tarea de Firebase ejecutándose inmediatamente...");

        // Reiniciar el temporizador de ejecución periódica
        scheduleFirebaseWorker();
    }
}
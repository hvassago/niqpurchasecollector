package com.niq.niqpurchasecollector;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import com.niq.niqpurchasecollector.databinding.ActivityMainBinding;

import android.Manifest;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Intent;
import android.provider.Settings;
import android.provider.MediaStore;
import android.widget.RelativeLayout;


public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private static final int REQUEST_CODE_ALL_FILES_ACCESS = 1001;
    private static final int REQUEST_CODE_CAMERA = 2001;
    private Uri photoUri;
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private static final int REQUEST_CODE_GALLERY = 3001;

    private MediaRecorder mediaRecorder;
    private String audioFilePath;

    @SuppressLint("QueryPermissionsNeeded")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView(R.layout.activity_main);

        // Combine permisos de audio y almacenamiento
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 y superior: Solicitar acceso a todos los archivos, micrófono y cámara
            if (!Environment.isExternalStorageManager() ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                // Solicitar acceso a todos los archivos
                Intent getPermissionIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(getPermissionIntent, REQUEST_CODE_ALL_FILES_ACCESS);

                // Solicitar permisos adicionales
                requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                        1001
                );
            } else {
                createDirectory();
            }
        } else {
            // Android 7 a Android 10
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                        1
                );
            } else {
                createDirectory();
            }
        }

        // Continuar después de verificar permisos
        initializeApp();
    }

    // Método para abrir la cámara
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

    // Crear archivo para guardar la imagen
    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
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
            Toast.makeText(this, "Error al crear archivo de imagen", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void startRecording() {
        try {
            // Ruta específica del directorio
            File storageDir = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

            // Crear el directorio si no existe
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e("Directorio", "Error al crear directorio: " + storageDir.getAbsolutePath());
                Toast.makeText(this, "No se pudo crear el directorio", Toast.LENGTH_SHORT).show();
                return;
            }

            // Nombre único para el archivo de audio
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "AUDIO_" + timeStamp + ".mp3"; // Formato de archivo
            File audioFile = new File(storageDir, fileName);
            audioFilePath = audioFile.getAbsolutePath(); // Guardar la ruta del archivo

            // Configurar MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); // Fuente: micrófono
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // Formato de salida
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); // Codificación
            mediaRecorder.setOutputFile(audioFilePath); // Archivo de salida
            mediaRecorder.prepare(); // Preparar el MediaRecorder
            mediaRecorder.start(); // Iniciar la grabación

            Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al iniciar la grabación", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            Toast.makeText(this, "Grabación guardada en: " + audioFilePath, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "No se pudo crear la carpeta para guardar los archivos", Toast.LENGTH_SHORT).show();
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
                createDirectory();
            } else {
                Toast.makeText(this, "Permiso para acceder a todos los archivos no otorgado", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK) {
            // Imagen capturada y guardada correctamente
            if (photoUri != null) {
                Toast.makeText(this, "Imagen guardada correctamente en: " + photoUri.getPath(), Toast.LENGTH_SHORT).show();
                Log.d("ImagenGuardada", "Imagen guardada en: " + photoUri.getPath());
            }
        } else if (requestCode == REQUEST_CODE_GALLERY && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // Múltiples imágenes seleccionadas
                int itemCount = data.getClipData().getItemCount();
                for (int i = 0; i < itemCount; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    saveImageGalleryToDirectory(imageUri, i); // Pasar el índice para nombres únicos
                }
            } else if (data.getData() != null) {
                // Una sola imagen seleccionada
                Uri imageUri = data.getData();
                saveImageGalleryToDirectory(imageUri, 0); // Usar índice 0 para una imagen única
            }
        }
    }

    private void saveImageGalleryToDirectory(Uri imageUri, int index) {
        try {
            // Ruta específica del directorio
            File storageDir = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/recursoscolectados");

            // Crear el directorio si no existe
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e("Directorio", "Error al crear directorio: " + storageDir.getAbsolutePath());
                Toast.makeText(this, "No se pudo crear el directorio", Toast.LENGTH_SHORT).show();
                return;
            }

            // Crear un archivo con nombre único para guardar la imagen
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "IMG_" + timeStamp + "_" + index + ".jpg"; // Agregar índice al nombre
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

            Toast.makeText(this, "Imagen guardada: " + imageFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            Log.d("GuardarImagen", "Imagen guardada en: " + imageFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al guardar la imagen", Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createDirectory();
            } else {
                Toast.makeText(this, "Permiso denegado para escribir en el almacenamiento externo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeApp() {
        // Configurar la interfaz de usuario
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        binding.appBarMain.fab.setOnClickListener(view -> {
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab).show();
        });

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
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("image/*".equals(type) || "application/pdf".equals(type)) {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    // Ruta específica en Android/media/com.niq.niqpurchasecollector
                    File mediaDirectory = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/archivos_recibidos");

                    // Verifica si la carpeta existe, de lo contrario, créala
                    if (!mediaDirectory.exists()) {
                        if (mediaDirectory.mkdirs()) {
                            Log.d("Directorio", "Carpeta creada exitosamente: " + mediaDirectory.getAbsolutePath());
                        } else {
                            Log.e("Directorio", "Error al crear la carpeta: " + mediaDirectory.getAbsolutePath());
                            Toast.makeText(this, "No se pudo crear la carpeta para guardar los archivos", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    // Nombre del archivo basado en la hora actual y el tipo de archivo
                    String fileName = "archivo_" + System.currentTimeMillis() + "." +
                            MimeTypeMap.getSingleton().getExtensionFromMimeType(getContentResolver().getType(uri));
                    File file = new File(mediaDirectory, fileName);

                    try {
                        // Copiar contenido del archivo compartido al destino
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        OutputStream outputStream = new FileOutputStream(file);

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                        inputStream.close();
                        outputStream.close();

                        Toast.makeText(this, "Archivo guardado correctamente en: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        Log.d("ArchivoGuardado", "Archivo guardado en: " + file.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error al guardar el archivo", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
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
}
package com.niq.niqpurchasecollector;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
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

    /**
     * Se llama cuando la actividad se crea por primera vez.
     * Este método inicializa Firebase, configura Firebase App Check para seguridad,
     * inicializa la interfaz de usuario de la aplicación y solicita los permisos necesarios.
     *
     * Firebase App Check se configura para usar Play Integrity en compilaciones de producción
     * y un proveedor de depuración para compilaciones de desarrollo para garantizar que los recursos
     * de backend de la aplicación estén protegidos contra abusos.
     *
     * Después de la inicialización de Firebase y App Check, procede a inicializar
     * el resto de los componentes de la aplicación y luego solicita los permisos de tiempo de ejecución
     * necesarios para la funcionalidad de la aplicación.
     *
     * @param savedInstanceState Si la actividad se está reinicializando después
     *     de haber sido cerrada previamente, este Bundle contiene los datos que
     *     suministró más recientemente en {@link #onSaveInstanceState}.  <b><i>Nota: De lo contrario, es nulo.</i></b>
     */
    @SuppressLint("QueryPermissionsNeeded")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        // SOLUCIÓN: Usar Play Integrity para producción o Debug solo para desarrollo
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

        // Continuar después de verificar permisos
        initializeApp();

        requestAppPermissions();
    }

    /**
     * Solicita los permisos necesarios de la aplicación.
     *
     * Este método comprueba si los permisos requeridos (almacenamiento externo, grabar audio, cámara, internet)
     * están concedidos. Si no, los solicita al usuario.
     *
     * Para Android R (API 30) y superior, solicita específicamente el permiso `MANAGE_EXTERNAL_STORAGE`
     * junto con otros permisos estándar. Para versiones anteriores, solicita
     * `WRITE_EXTERNAL_STORAGE`.
     *
     * Si todos los permisos ya están concedidos, procede a llamar a `allPermissionsGranted()`.
     */
    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager() ||
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {

                // Pedir permisos especiales
                Intent getPermissionIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(getPermissionIntent, REQUEST_CODE_ALL_FILES_ACCESS);

                requestPermissions(
                        new String[]{
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                                Manifest.permission.INTERNET
                        },
                        REQUEST_CODE_ALL_FILES_ACCESS
                );
            } else {
                allPermissionsGranted();
            }
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA,
                                Manifest.permission.INTERNET
                        },
                        REQUEST_CODE_ALL_FILES_ACCESS
                );
            } else {
                allPermissionsGranted();
            }
        }
    }

    /**
     * Respuesta a la solicitud de permisos. Este método se invoca para cada llamada a
     * {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Nota:</strong> Es posible que la interacción de solicitud de permisos
     * con el usuario sea interrumpida. En este caso, recibirás arrays de permisos
     * y resultados vacíos que deben tratarse como una cancelación.
     * </p>
     *
     * @param requestCode El código de solicitud pasado en {@link #requestPermissions(String[], int)}.
     * @param permissions Los permisos solicitados. Nunca es nulo.
     * @param grantResults Los resultados de la concesión para los permisos correspondientes
     *     que pueden ser {@link PackageManager#PERMISSION_GRANTED}
     *     o {@link PackageManager#PERMISSION_DENIED}. Nunca es nulo.
     *
     * @see #requestPermissions(String[], int)
     */ // Cuando los permisos sean concedidos, se ejecutarán las funciones necesarias
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

    /**
     * Este método se ejecuta cuando se otorgan todos los permisos necesarios.
     * Realiza las siguientes acciones:
     * 1. Crea un directorio si no existe.
     * 2. Verifica la configuración de la aplicación.
     * 3. Si el worker de Firebase no está programado, lo programa y lo marca como programado.
     * 4. Si el worker de Firebase ya está programado, lo ejecuta inmediatamente.
     */ // Método que se ejecuta solo cuando los permisos son otorgados
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
    /**
     * Abre la cámara del dispositivo para capturar una imagen.
     * <p>
     * Este método crea un {@link Intent} con {@link MediaStore#ACTION_IMAGE_CAPTURE}
     * para iniciar la aplicación de la cámara. Primero comprueba si existe una aplicación
     * que pueda manejar este intent. Si es así, procede a crear un archivo temporal
     * donde se almacenará la imagen capturada.
     * </p>
     * <p>
     * El archivo de imagen se crea utilizando {@link #createImageFile()}. Si el archivo se
     * crea con éxito, su URI se obtiene mediante {@link FileProvider#getUriForFile(android.content.Context, String, File)}
     * y se pasa al intent de la cámara como un extra ({@link MediaStore#EXTRA_OUTPUT}).
     * Esto asegura que la aplicación de la cámara guarde la imagen en el archivo especificado.
     * </p>
     * <p>
     * Finalmente, se llama a {@link #startActivityForResult(Intent, int)} con el intent de la cámara
     * y un código de solicitud ({@link #REQUEST_CODE_CAMERA}). El resultado de esta actividad
     * (la imagen capturada) se manejará en el método {@link #onActivityResult(int, int, Intent)}.
     * </p>
     * <p>
     * Si no se encuentra ninguna aplicación de cámara en el dispositivo, se muestra un mensaje {@link Toast}
     * "No se encontró aplicación de cámara" al usuario.
     * </p>
     */ // Método para abrir la cámara
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

    /**
     * Abre la galería del dispositivo para permitir al usuario seleccionar una o más imágenes.
     * Crea un Intent con `ACTION_GET_CONTENT` para seleccionar archivos de imagen.
     * `EXTRA_ALLOW_MULTIPLE` se establece en verdadero para habilitar la selección de múltiples imágenes.
     * La(s) imagen(es) seleccionada(s) se manejarán en el método `onActivityResult`
     * con el código de solicitud `REQUEST_CODE_GALLERY`.
     */
    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Permitir múltiples selecciones
        startActivityForResult(Intent.createChooser(galleryIntent, "Selecciona imágenes"), REQUEST_CODE_GALLERY);
    }

    /**
     * Crea un archivo de imagen en el directorio de almacenamiento designado.
     * El nombre del archivo se genera usando el {@code smsId} y una marca de tiempo para asegurar la unicidad.
     * La imagen se guardará en el directorio "Android/media/com.niq.niqpurchasecollector/recursoscolectados"
     * en el almacenamiento externo. Si el directorio no existe, se creará.
     *
     * @return Un objeto {@link File} que representa el archivo de imagen creado, o {@code null} si ocurrió un error.
     */ // Crear archivo para guardar la imagen
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

    /**
     * Inicia la actividad de grabación de voz.
     * Este método crea una intención para iniciar la actividad {@link activity_voice_recorder},
     * que gestiona la funcionalidad de grabación de voz.
     */
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

    /**
     * Maneja el resultado de una actividad lanzada para obtener un resultado.
     * Este método se llama cuando una actividad lanzada por {@link #startActivityForResult(Intent, int)}
     * devuelve un resultado. Procesa resultados para varios códigos de solicitud, como:
     * - {@link #REQUEST_CODE_ALL_FILES_ACCESS}: Maneja el resultado de la solicitud de permiso de acceso a todos los archivos.
     *   Si se concede, llama a {@link #allPermissionsGranted()}.
     * - {@link #REQUEST_CODE_CAMERA}: Maneja el resultado de capturar una imagen con la cámara.
     *   Si tiene éxito, registra la ruta de la imagen guardada.
     * - {@link #REQUEST_CODE_GALLERY}: Maneja el resultado de seleccionar imagen(es) de la galería.
     *   Guarda la(s) imagen(es) seleccionada(s) en el directorio designado de la aplicación.
     * - {@link #REQUEST_CODE_FILE_PICKER}: Maneja el resultado de seleccionar un archivo usando el selector de archivos.
     *   Si tiene éxito, guarda el archivo seleccionado en el directorio designado de la aplicación.
     *
     * Después de procesar la solicitud específica, verifica si todos los permisos necesarios están concedidos
     * y llama a {@link #allPermissionsGranted()} si lo están.
     *
     * @param requestCode El código de solicitud entero suministrado originalmente a
     *                    startActivityForResult(), permitiéndole identificar de quién
     *                    proviene este resultado.
     * @param resultCode El código de resultado entero devuelto por la actividad secundaria
     *                   a través de su setResult().
     * @param data Un Intent, que puede devolver datos de resultado al llamador
     *               (se pueden adjuntar varios datos a los "extras" del Intent).
     */
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
        //Verificar modo de ejecución
        if (BuildConfig.DEBUG) {
            Log.d("BuildConfig", "MODO DEBUG");
        } else {
            Log.d("BuildConfig", "MODO RELEASE");
        }
        // Configurar la interfaz de usuario
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

/*        binding.appBarMain.fab.setOnClickListener(view -> {
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab).show();
        });*/

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
        PeriodicWorkRequest firebaseWorkRequest = new PeriodicWorkRequest.Builder(FirebaseWorker.class, 1, TimeUnit.HOURS)
                .build();
        Log.d("scheduleFirebaseWorker()", "Programando tarea Firebase");
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "FirebaseWorkerJob",
                ExistingPeriodicWorkPolicy.KEEP, // Mantiene el temporizador si ya existe
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
        WorkManager workManager = WorkManager.getInstance(this);
        Log.d("executeFirebaseNow()", "Ejecutando");

        // Crear la tarea inmediata con un pequeño retraso para asegurar su encolamiento
        OneTimeWorkRequest firebaseWorkRequest = new OneTimeWorkRequest.Builder(FirebaseWorker.class)
                .setInitialDelay(1, TimeUnit.SECONDS) // Pequeño retraso para evitar problemas de encolamiento
                .build();

        workManager.enqueueUniqueWork("FirebaseWorkerOneTime", ExistingWorkPolicy.REPLACE, firebaseWorkRequest);
        Log.d("FirebaseWorker", "Tarea de Firebase ejecutándose inmediatamente...");

        // Reiniciar el temporizador de ejecución periódica
        scheduleFirebaseWorker();
    }
}
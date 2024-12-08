package com.niq.niqpurchasecollector;

import android.content.pm.PackageManager;
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

import android.content.Intent;
import android.provider.Settings;


public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private static final int REQUEST_CODE_ALL_FILES_ACCESS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Combine permisos de audio y almacenamiento
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 y superior: Solicitar acceso a todos los archivos y micrófono
            if (!Environment.isExternalStorageManager() || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_CODE_ALL_FILES_ACCESS);
                // También solicitar permiso de micrófono, si es necesario
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            } else {
                createDirectory();
            }
        } else {
            // Android 7 a Android 10
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO}, 1);
            } else {
                createDirectory();
            }
        }
        // Continuar después de verificar permisos
        initializeApp();
    }


    private void createDirectory() {
        // Ruta a Android/media/com.niq.niqpurchasecollector
        File directory = new File(Environment.getExternalStorageDirectory(), "Android/media/com.niq.niqpurchasecollector/archivos_recibidos");

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
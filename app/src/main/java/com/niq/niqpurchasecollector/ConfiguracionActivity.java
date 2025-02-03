package com.niq.niqpurchasecollector;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class ConfiguracionActivity extends AppCompatActivity {

    private TextInputEditText etSmsId, etTelNumber;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        etSmsId = findViewById(R.id.etSmsId);
        etTelNumber = findViewById(R.id.etTelNumber);
        Button btnGuardar = findViewById(R.id.btnGuardar);

        sharedPreferences = getSharedPreferences("ConfigTienda", Context.MODE_PRIVATE);

        btnGuardar.setOnClickListener(v -> guardarConfiguracion());
    }

    private void guardarConfiguracion() {
        String smsId = etSmsId.getText().toString().trim();
        String telNumber = etTelNumber.getText().toString().trim();

        // Validar que los campos no estén vacíos
        if (smsId.isEmpty() || telNumber.isEmpty()) {
            Toast.makeText(this, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validar que smsId tenga exactamente 10 dígitos numéricos
        if (!smsId.matches("^\\d{10}$")) {
            Toast.makeText(this, "El ID SMS debe tener exactamente 10 dígitos numéricos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validar que telNumber tenga exactamente 8 dígitos numéricos
        if (!telNumber.matches("^\\d{8}$")) {
            Toast.makeText(this, "El número de teléfono debe tener exactamente 8 dígitos numéricos", Toast.LENGTH_SHORT).show();
            return;
        }

        // Guardar configuración si todo es válido
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("smsid", smsId);
        editor.putString("telnumber", telNumber);
        editor.apply();

        Toast.makeText(this, "Configuración guardada exitosamente", Toast.LENGTH_SHORT).show();

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

}
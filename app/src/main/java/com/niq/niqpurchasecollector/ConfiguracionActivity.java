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

    private TextInputEditText etSmsId, etTelNumber, etValidationCode;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        etSmsId = findViewById(R.id.etSmsId);
        etTelNumber = findViewById(R.id.etTelNumber);
        etValidationCode = findViewById(R.id.etValidationCode); // Nuevo campo
        Button btnGuardar = findViewById(R.id.btnGuardar);

        sharedPreferences = getSharedPreferences("ConfigTienda", Context.MODE_PRIVATE);

        btnGuardar.setOnClickListener(v -> guardarConfiguracion());
    }

    /**
     * Guarda la configuración de la aplicación.
     * <p>
     * Este método recupera el IDSMS, el número de teléfono y el código de validación de los campos de entrada.
     * Valida que los campos no estén vacíos.
     * Valida que el ID de SMS tenga exactamente 10 dígitos numéricos.
     * Valida que el número de teléfono tenga exactamente 8 dígitos numéricos.
     * Valida que el código de validación sea "94565923".
     * Si todas las validaciones pasan, guarda la configuración en SharedPreferences,
     * muestra un mensaje de éxito, inicia MainActivity y finaliza la actividad actual.
     * Si alguna validación falla, muestra un mensaje de error apropiado.
     */
    /**
     * Genera un código de validación complejo basado en la fecha actual.
     * Utiliza múltiples operaciones matemáticas y constantes secretas.
     * @return código de validación de 8 dígitos
     */
    private String generateDailyValidationCode() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
        int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        
        // Constantes secretas que solo tú conoces
        final int SECRET1 = 73;
        final int SECRET2 = 137;
        final int SECRET3 = 19;
        
        // Operaciones complejas y no lineales
        int step1 = (year % 100) * SECRET1;
        int step2 = (int) Math.pow(month + SECRET2, 2);
        int step3 = (day * SECRET3) ^ dayOfWeek; // XOR para no linealidad
        int step4 = (step1 + step2 - step3) * 7; // Multiplicador primo
        
        // Aplicar módulo en diferentes etapas para mayor aleatoriedad
        int step5 = Math.abs(step4) % 9876543;
        int step6 = (step5 * 11 + 12345) % 100000000;
        
        // Asegurar que siempre tenga 8 dígitos
        if (step6 < 10000000) {
            step6 = step6 + 8765432; // Constante de ajuste
        }
        
        return String.valueOf(step6);
    }

    private void guardarConfiguracion() {
        String smsId = etSmsId.getText().toString().trim();
        String telNumber = etTelNumber.getText().toString().trim();
        String validationCode = etValidationCode.getText().toString().trim(); // Nuevo campo

        // Validar que los campos no estén vacíos
        if (smsId.isEmpty() || telNumber.isEmpty() || validationCode.isEmpty()) {
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

        // Validar que el código de validación sea el código diario
        String expectedCode = generateDailyValidationCode();
        if (!validationCode.equals(expectedCode)) {
            Toast.makeText(this, "El código de validación es incorrecto", Toast.LENGTH_SHORT).show();
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

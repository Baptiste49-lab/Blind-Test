package fr.esaip.blindtest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import fr.esaip.blindtest.R;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private TextView tvForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Coupe l'animation d'ouverture de l'application
        overridePendingTransition(0, 0);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mAuth = FirebaseAuth.getInstance();

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        btnLogin.setOnClickListener(v -> handleAuth(true));
        btnRegister.setOnClickListener(v -> handleAuth(false));
        tvForgotPassword.setOnClickListener(v -> resetPassword());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToConfig();
        }
    }

    private void handleAuth(boolean isLogin) {
        tilEmail.setError(null);
        tilPassword.setError(null);

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        boolean hasError = false;

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Format d'e-mail invalide");
            hasError = true;
        }

        if (password.isEmpty() || password.length() < 6) {
            tilPassword.setError("Minimum 6 caractères");
            hasError = true;
        }

        if (hasError) return;

        if (isLogin) {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            goToConfig();
                        } else {
                            tilPassword.setError("Identifiants incorrects");
                        }
                    });
        } else {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Compte créé !", Toast.LENGTH_SHORT).show();
                            goToConfig();
                        } else {
                            tilEmail.setError("Erreur : e-mail déjà utilisé ou invalide");
                        }
                    });
        }
    }

    private void resetPassword() {
        tilEmail.setError(null);
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Saisissez votre e-mail pour réinitialiser");
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Lien envoyé par e-mail", Toast.LENGTH_LONG).show();
                    } else {
                        tilEmail.setError("E-mail introuvable");
                    }
                });
    }

    private void goToConfig() {
        Intent intent = new Intent(AuthActivity.this, ConfigActivity.class);
        // Coupe l'animation de transition vers le prochain écran
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}
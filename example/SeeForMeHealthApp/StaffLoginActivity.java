package com.example.SeeForMeHealthApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class StaffLoginActivity extends AppCompatActivity {

    private EditText etStaffId, etPassword;
    private TextView tvError, tvHospitalName;
    private Button btnLogin;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    private static final String PREFS            = "SeeForMePrefs";
    private static final String KEY_STAFF_LOGGED = "staff_logged_in";
    private static final String KEY_HOSPITAL     = "logged_hospital";
    private static final String KEY_STAFF_ID     = "staff_id";

    private static final String[][] HOSPITAL_ACCOUNTS = {
            {"MULAGO001", "Mulago National Referral Hospital", "mulago@seeformehealth.com"},
            {"RUBAGA001", "Rubaga Hospital",                  "rubaga@seeformehealth.com"},
            {"MENGO001",  "Mengo Hospital",                   "mengo@seeformehealth.com"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_login);

        mAuth          = FirebaseAuth.getInstance();
        etStaffId      = findViewById(R.id.et_staff_id);
        etPassword     = findViewById(R.id.et_password);
        tvError        = findViewById(R.id.tv_error);
        tvHospitalName = findViewById(R.id.tv_hospital_name);
        btnLogin       = findViewById(R.id.btn_login);
        progressBar    = findViewById(R.id.progress_bar);

        // Skip login if already logged in
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_STAFF_LOGGED, false)) {
            goToDashboard();
            return;
        }

        // Show hospital name as staff types their ID
        etStaffId.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                String id       = s.toString().toUpperCase().trim();
                String hospital = getHospitalForId(id);
                if (tvHospitalName != null) {
                    tvHospitalName.setText(hospital);
                }
            }
        });

        // Login button
        btnLogin.setOnClickListener(v -> attemptLogin());

        // Back button
        Button btnGoBack = findViewById(R.id.btn_go_back);
        if (btnGoBack != null) {
            btnGoBack.setOnClickListener(v -> {
                startActivity(new Intent(StaffLoginActivity.this, ModeActivity.class));
                finish();
            });
        }
    }

    private void attemptLogin() {
        String staffId  = etStaffId.getText().toString().trim().toUpperCase();
        String password = etPassword.getText().toString().trim();

        tvError.setText("");

        if (staffId.isEmpty()) {
            tvError.setText("Please enter your Staff ID.");
            return;
        }
        if (password.isEmpty()) {
            tvError.setText("Please enter your password.");
            return;
        }

        String email    = getEmailForId(staffId);
        String hospital = getHospitalForId(staffId);

        if (email.isEmpty()) {
            tvError.setText("Staff ID not recognised. Please check and try again.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_STAFF_LOGGED, true)
                            .putString(KEY_HOSPITAL, hospital)
                            .putString(KEY_STAFF_ID, staffId)
                            .apply();

                    goToDashboard();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    String msg = e.getMessage();
                    if (msg != null && msg.contains("password")) {
                        tvError.setText("Incorrect password. Please try again.");
                    } else if (msg != null && msg.contains("network")) {
                        tvError.setText("No internet connection. Please check your network.");
                    } else if (msg != null && msg.contains("user")) {
                        tvError.setText("Account not found. Please contact your administrator.");
                    } else {
                        tvError.setText("Login failed. Please check your credentials.");
                    }
                });
    }

    private void goToDashboard() {
        startActivity(new Intent(StaffLoginActivity.this, StaffDashboardActivity.class));
        finish();
    }

    private String getHospitalForId(String staffId) {
        for (String[] account : HOSPITAL_ACCOUNTS) {
            if (account[0].equals(staffId)) return account[1];
        }
        return "";
    }

    private String getEmailForId(String staffId) {
        for (String[] account : HOSPITAL_ACCOUNTS) {
            if (account[0].equals(staffId)) return account[2];
        }
        return "";
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, ModeActivity.class));
        finish();
    }
}
package com.example.SeeForMeHealthApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.firebase.auth.FirebaseAuth;

public class StaffDashboardActivity extends AppCompatActivity {

    private static final String PREFS            = "SeeForMePrefs";
    private static final String KEY_HOSPITAL     = "logged_hospital";
    private static final String KEY_STAFF_LOGGED = "staff_logged_in";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_dashboard);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String hospital         = prefs.getString(KEY_HOSPITAL, "");
        String staffId          = prefs.getString("staff_id", "");

        // Show hospital name and staff ID
        TextView tvHospital = findViewById(R.id.tv_hospital_name);
        TextView tvStaffId  = findViewById(R.id.tv_staff_id);
        if (tvHospital != null) tvHospital.setText(hospital);
        if (tvStaffId  != null) tvStaffId.setText("Staff ID: " + staffId);

        // Appointments card — pass hospital filter
        CardView cardAppointments = findViewById(R.id.cardAppointments);
        cardAppointments.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppointmentActivity.class);
            intent.putExtra("HOSPITAL_FILTER", hospital);
            startActivity(intent);
        });

        // Logout button
        Button btnLogout = findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logoutStaff());
        }
    }

    private void logoutStaff() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_STAFF_LOGGED, false)
                .remove(KEY_HOSPITAL)
                .remove("staff_id")
                .apply();
        startActivity(new Intent(this, ModeActivity.class));
        finish();
    }
}
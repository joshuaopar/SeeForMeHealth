package com.example.SeeForMeHealthApp;

import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppointmentActivity extends AppCompatActivity {

    private static final String DB_URL = "https://see-for-me-health-default-rtdb.firebaseio.com";

    private RecyclerView recyclerView;
    private AppointmentAdapter adapter;
    private final List<Appointment> appointmentList = new ArrayList<>();   // all loaded
    private final List<Appointment> displayList = new ArrayList<>();      // filtered/shown
    private DatabaseReference dbRef;

    private String hospitalFilter = "";
    private String currentStatusFilter = "ALL"; // ALL, PENDING, CONFIRMED, CANCELLED, ARCHIVED
    private String currentSearchText = "";

    private EditText etSearch;
    private Button btnAll, btnPending, btnConfirmed, btnRejected, btnArchived;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointment_staff);

        hospitalFilter = getIntent().getStringExtra("HOSPITAL_FILTER");
        if (hospitalFilter == null) hospitalFilter = "";

        TextView tvTitle = findViewById(R.id.tv_appointments_title);
        if (tvTitle != null) {
            tvTitle.setText(hospitalFilter.isEmpty()
                    ? "All Appointments"
                    : hospitalFilter + " — Appointments");
        }

        recyclerView = findViewById(R.id.recycler_appointments);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AppointmentAdapter(this, displayList,
                new AppointmentAdapter.OnActionListener() {
                    @Override
                    public void onConfirm(Appointment appt) {
                        updateStatus(appt, "CONFIRMED",
                                appt.getPatientName() + " CONFIRMED");
                    }

                    @Override
                    public void onCancel(Appointment appt) {
                        updateStatus(appt, "CANCELLED",
                                appt.getPatientName() + " CANCELLED");
                    }
                });

        recyclerView.setAdapter(adapter);

        setupSearchAndFilters();
        loadAppointments();
    }

    private void setupSearchAndFilters() {
        etSearch = findViewById(R.id.et_search);
        btnAll = findViewById(R.id.btn_filter_all);
        btnPending = findViewById(R.id.btn_filter_pending);
        btnConfirmed = findViewById(R.id.btn_filter_confirmed);
        btnRejected = findViewById(R.id.btn_filter_rejected);
        btnArchived = findViewById(R.id.btn_filter_archived);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    currentSearchText = s.toString().trim();
                    applyFilters();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        View.OnClickListener filterClick = v -> {
            if (v == btnAll) currentStatusFilter = "ALL";
            else if (v == btnPending) currentStatusFilter = "PENDING";
            else if (v == btnConfirmed) currentStatusFilter = "CONFIRMED";
            else if (v == btnRejected) currentStatusFilter = "CANCELLED";
            else if (v == btnArchived) currentStatusFilter = "ARCHIVED";

            highlightActiveFilter(v);
            applyFilters();
        };

        if (btnAll != null) btnAll.setOnClickListener(filterClick);
        if (btnPending != null) btnPending.setOnClickListener(filterClick);
        if (btnConfirmed != null) btnConfirmed.setOnClickListener(filterClick);
        if (btnRejected != null) btnRejected.setOnClickListener(filterClick);
        if (btnArchived != null) btnArchived.setOnClickListener(filterClick);
    }

    private void highlightActiveFilter(View active) {
        Button[] all = {btnAll, btnPending, btnConfirmed, btnRejected, btnArchived};
        for (Button b : all) {
            if (b == null) continue;
            if (b == active) {
                b.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF00796B));
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFCCCCCC));
                b.setTextColor(0xFF333333);
            }
        }
    }

    private void loadAppointments() {
        dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("appointments");

        dbRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                appointmentList.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {

                    String apptHospital = ds.child("hospital").getValue(String.class);

                    if (!hospitalFilter.isEmpty()) {
                        if (apptHospital == null) continue;

                        boolean matches =
                                apptHospital.equalsIgnoreCase(hospitalFilter) ||
                                        hospitalFilter.toLowerCase().contains(
                                                apptHospital.toLowerCase()) ||
                                        apptHospital.toLowerCase().contains(
                                                hospitalFilter.toLowerCase());

                        if (!matches) continue;
                    }

                    Appointment appt = new Appointment();
                    appt.setId(ds.getKey());
                    appt.setPatientName(
                            ds.child("patientName").getValue(String.class));
                    appt.setPatientPhone(
                            ds.child("patientPhone").getValue(String.class));
                    appt.setHospital(
                            ds.child("hospital").getValue(String.class));
                    appt.setHospitalPhone(
                            ds.child("hospitalPhone").getValue(String.class));
                    appt.setSpeciality(
                            ds.child("speciality").getValue(String.class));
                    appt.setDoctorName(
                            ds.child("doctorName").getValue(String.class));
                    appt.setDate(
                            ds.child("date").getValue(String.class));
                    appt.setTime(
                            ds.child("time").getValue(String.class));
                    appt.setStatus(
                            ds.child("status").getValue(String.class));
                    appt.setTimestamp(
                            ds.child("timestamp").getValue(Long.class));
                    appt.setArchived(
                            Boolean.TRUE.equals(ds.child("archived").getValue(Boolean.class)));

                    appointmentList.add(appt);
                }

                Collections.sort(appointmentList, (a, b) -> {
                    long tA = a.getTimestamp() != null ? a.getTimestamp() : 0;
                    long tB = b.getTimestamp() != null ? b.getTimestamp() : 0;
                    return Long.compare(tB, tA);
                });

                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AppointmentActivity.this,
                        "Failed to load appointments.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilters() {
        displayList.clear();

        for (Appointment appt : appointmentList) {
            boolean isArchived = appt.isArchived();

            // Archived tab shows only archived; all other tabs hide archived
            if (currentStatusFilter.equals("ARCHIVED")) {
                if (!isArchived) continue;
            } else {
                if (isArchived) continue;

                if (!currentStatusFilter.equals("ALL")) {
                    String status = appt.getStatus() == null ? "PENDING" : appt.getStatus();
                    if (!status.equalsIgnoreCase(currentStatusFilter)) continue;
                }
            }

            if (!currentSearchText.isEmpty()) {
                String name = appt.getPatientName() == null ? "" : appt.getPatientName().toLowerCase();
                String phone = appt.getPatientPhone() == null ? "" : appt.getPatientPhone();
                String q = currentSearchText.toLowerCase();
                if (!name.contains(q) && !phone.contains(currentSearchText)) continue;
            }

            displayList.add(appt);
        }

        adapter.notifyDataSetChanged();

        LinearLayout tvEmpty = findViewById(R.id.tv_empty);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(
                    displayList.isEmpty()
                            ? View.VISIBLE
                            : View.GONE);
        }
    }

    private void updateStatus(Appointment appt, String newStatus, String toastMsg) {
        FirebaseDatabase.getInstance(DB_URL)
                .getReference("appointments")
                .child(appt.getId())
                .child("status")
                .setValue(newStatus)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, toastMsg,
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed. Check internet.",
                                Toast.LENGTH_SHORT).show());
    }

    public void archiveAppointment(Appointment appt) {
        boolean newValue = !appt.isArchived();
        FirebaseDatabase.getInstance(DB_URL)
                .getReference("appointments")
                .child(appt.getId())
                .child("archived")
                .setValue(newValue)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, newValue ? "Archived" : "Unarchived", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to archive", Toast.LENGTH_SHORT).show());
    }
}
package com.example.SeeForMeHealthApp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {

    public interface OnActionListener {
        void onConfirm(Appointment appt);
        void onCancel(Appointment appt);
    }

    private final Context context;
    private final List<Appointment> list;
    private final OnActionListener listener;

    public AppointmentAdapter(Context context, List<Appointment> list, OnActionListener listener) {
        this.context  = context;
        this.list     = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_appointment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Appointment appt = list.get(position);

        h.tvPatientName.setText(safe(appt.getPatientName()));
        h.tvPhone.setText("Phone: " + safe(appt.getPatientPhone()));
        h.tvHospital.setText("Hospital: " + safe(appt.getHospital()) + " - " + safe(appt.getSpeciality()));
        h.tvDoctor.setText("Doctor: " + safe(appt.getDoctorName()));

        String arrivedAt = "";
        if (appt.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.getDefault());
            arrivedAt = "\nReceived: " + sdf.format(new Date(appt.getTimestamp()));
        }
        h.tvDateTime.setText("Date: " + safe(appt.getDate()) + "  Time: " + safe(appt.getTime()) + arrivedAt);

        String status = appt.getStatus() != null ? appt.getStatus() : "PENDING";
        h.tvStatus.setText(status);
        switch (status) {
            case "CONFIRMED":
                h.tvStatus.setBackgroundColor(Color.parseColor("#28A745"));
                break;
            case "CANCELLED":
                h.tvStatus.setBackgroundColor(Color.parseColor("#DC3545"));
                break;
            default:
                h.tvStatus.setBackgroundColor(Color.parseColor("#FFC107"));
                break;
        }

        h.btnCall.setOnClickListener(v -> {
            String phone = appt.getPatientPhone();
            if (phone != null && !phone.isEmpty()) {
                Intent call = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                context.startActivity(call);
            }
        });

        h.btnConfirm.setOnClickListener(v -> listener.onConfirm(appt));
        h.btnReject.setOnClickListener(v -> listener.onCancel(appt));

        // Archive / Unarchive
        boolean isArchived = appt.isArchived();
        h.btnArchive.setText(isArchived ? "Unarchive" : "Archive");
        h.btnConfirm.setVisibility(isArchived ? View.GONE : View.VISIBLE);
        h.btnReject.setVisibility(isArchived ? View.GONE : View.VISIBLE);

        h.btnArchive.setOnClickListener(v -> {
            if (context instanceof AppointmentActivity) {
                ((AppointmentActivity) context).archiveAppointment(appt);
            }
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    private String safe(String s) { return s != null ? s : ""; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatus, tvPatientName, tvPhone, tvHospital, tvDoctor, tvDateTime;
        Button btnCall, btnConfirm, btnReject, btnArchive;

        ViewHolder(@NonNull View v) {
            super(v);
            tvStatus      = v.findViewById(R.id.tvStatus);
            tvPatientName = v.findViewById(R.id.tvPatientName);
            tvPhone       = v.findViewById(R.id.tvPhone);
            tvHospital    = v.findViewById(R.id.tvHospital);
            tvDoctor      = v.findViewById(R.id.tvDoctor);
            tvDateTime    = v.findViewById(R.id.tvDateTime);
            btnCall       = v.findViewById(R.id.btnCall);
            btnConfirm    = v.findViewById(R.id.btnConfirm);
            btnReject     = v.findViewById(R.id.btnReject);
            btnArchive    = v.findViewById(R.id.btnArchive);
        }
    }
}
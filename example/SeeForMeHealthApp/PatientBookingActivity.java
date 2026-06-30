package com.example.SeeForMeHealthApp;

import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.TextView;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PatientBookingActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String DB_URL = "https://see-for-me-health-default-rtdb.firebaseio.com";

    private TextToSpeech tts;
    private TextView tvStatus;
    private String patientName, patientPhone;
    private String chosenHospital, chosenSpeciality, chosenDoctor, chosenDate, chosenTime;
    private DatabaseReference dbRef;
    private ValueEventListener confirmationListener;
    private DatabaseReference statusRef;
    private SpeechRecognizer speechRecognizer;
    private boolean isLeaving = false;

    private final Map<String, String> hospitalPhones = new HashMap<String, String>() {{
        put("mulago", "+256414541884");
        put("rubaga", "+256414274115");
        put("mengo",  "+256414270222");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointment);

        tvStatus = findViewById(R.id.tv_status);

        SharedPreferences prefs = getSharedPreferences("SeeForMePrefs", MODE_PRIVATE);
        patientName  = prefs.getString("patient_name", "Patient");
        patientPhone = prefs.getString("patient_phone", "");

        chosenHospital   = getIntent().getStringExtra("hospital");
        chosenSpeciality = getIntent().getStringExtra("speciality");
        chosenDoctor     = getIntent().getStringExtra("doctorName");
        chosenDate       = getIntent().getStringExtra("date");
        chosenTime       = getIntent().getStringExtra("time");

        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    if ("summary".equals(utteranceId)) {
                        runOnUiThread(() -> {
                            sendToFirebase();
                            startVoiceListening();
                        });
                    }
                }

                @Override public void onError(String utteranceId) {}
            });

            String summary = "Your appointment has been sent. Please wait, our staff will call you to confirm your appointment. Say leave app at any time to exit.";
            updateStatus("Appointment sent! Our staff will call you to confirm.");
            speak(summary, "summary");
        }
    }

    private void startVoiceListening() {
        if (isLeaving) return;
        if (speechRecognizer != null) speechRecognizer.destroy();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                if (isLeaving) return;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String match : matches) {
                        String heard = match.toLowerCase().trim();
                        if (heard.contains("leave") || heard.contains("exit")
                                || heard.contains("close")) {
                            leaveApp();
                            return;
                        }
                    }
                }
                // Keep listening
                new Handler().postDelayed(() -> startVoiceListening(), 1000);
            }

            @Override
            public void onError(int error) {
                if (isLeaving) return;
                new Handler().postDelayed(() -> startVoiceListening(), 1000);
            }

            @Override public void onReadyForSpeech(Bundle b) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int i, Bundle b) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void leaveApp() {
        isLeaving = true;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) tts.stop();
        tts.speak("Goodbye " + patientName + ". Take care.",
                TextToSpeech.QUEUE_FLUSH, null, null);
        new Handler().postDelayed(() -> finishAffinity(), 2500);
    }

    private void sendToFirebase() {
        dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("appointments");
        String key = dbRef.push().getKey();
        if (key == null) return;

        Map<String, Object> appt = new HashMap<>();
        appt.put("id",           key);
        appt.put("patientName",  patientName);
        appt.put("patientPhone", patientPhone);
        appt.put("hospital",     getHospitalFullName(chosenHospital));
        appt.put("hospitalPhone",hospitalPhones.get(
                chosenHospital != null ? chosenHospital.toLowerCase() : ""));
        appt.put("speciality",   chosenSpeciality);
        appt.put("doctorName",   chosenDoctor);
        appt.put("date",         chosenDate);
        appt.put("time",         chosenTime);
        appt.put("status",       "PENDING");
        appt.put("timestamp",    System.currentTimeMillis());

        dbRef.child(key).setValue(appt)
                .addOnSuccessListener(aVoid -> {
                    updateStatus("Appointment sent! Our staff will call you to confirm.");
                    listenForConfirmation(key);
                })
                .addOnFailureListener(e -> {
                    updateStatus("Failed to send. Check your internet.");
                    speak("Sorry, we could not send your appointment. Please try again.",
                            "error");
                });
    }

    private void listenForConfirmation(String appointmentKey) {
        statusRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("appointments")
                .child(appointmentKey)
                .child("status");

        confirmationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("CONFIRMED".equals(status)) {
                    runOnUiThread(() -> {
                        updateStatus("Appointment CONFIRMED!");
                        speak("Great news " + patientName +
                                "! Your appointment with " + chosenDoctor +
                                " at " + getHospitalFullName(chosenHospital) +
                                " on " + chosenDate + " at " + chosenTime +
                                " has been confirmed!", "confirmed");
                    });
                    statusRef.removeEventListener(confirmationListener);

                } else if ("CANCELLED".equals(status)) {
                    runOnUiThread(() -> {
                        updateStatus("Appointment Cancelled");
                        speak("Sorry " + patientName +
                                ", the hospital cancelled your appointment. " +
                                "Please try booking again.", "cancelled");
                    });
                    statusRef.removeEventListener(confirmationListener);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        statusRef.addValueEventListener(confirmationListener);
    }

    private String getHospitalFullName(String hospital) {
        if (hospital == null) return "";
        switch (hospital.toLowerCase()) {
            case "mulago": return "Mulago National Referral Hospital";
            case "rubaga": return "Rubaga Hospital";
            case "mengo":  return "Mengo Hospital";
            default:       return hospital;
        }
    }

    private void speak(String text, String utteranceId) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
    }

    private void updateStatus(String msg) {
        runOnUiThread(() -> { if (tvStatus != null) tvStatus.setText(msg); });
    }

    @Override
    protected void onDestroy() {
        if (statusRef != null && confirmationListener != null) {
            statusRef.removeEventListener(confirmationListener);
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
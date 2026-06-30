package com.example.SeeForMeHealthApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Locale;

public class PatientActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;
    private TextToSpeech tts;
    private Vibrator vibrator;
    private TextView tvUserName;
    private static final int SPEECH_REQUEST_CODE = 100;
    private boolean isListening = false;
    private boolean hasNavigated = false;
    private String userName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);
        setContentView(R.layout.activity_patient);

        tvUserName = findViewById(R.id.tvUserName);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("SeeForMePrefs", MODE_PRIVATE);
            userName = prefs.getString("patient_name", "");
        }

        if (!userName.isEmpty()) {
            tvUserName.setText(userName);
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(600);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(count -> {
            if (!isListening && !hasNavigated) {
                if (vibrator != null) vibrator.vibrate(400);
                startVoiceInput();
            }
        });

        findViewById(R.id.micCircle).setOnClickListener(v -> {
            if (!isListening && !hasNavigated) {
                if (vibrator != null) vibrator.vibrate(400);
                startVoiceInput();
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);

            String welcome = (!userName.isEmpty())
                    ? "See For Me is ready. " + userName + ", say Hospital, Emergency, or Leave App."
                    : "See For Me is ready. Say Hospital, Emergency, or Leave App.";

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        if (!hasNavigated && !isListening) {
                            startVoiceInput();
                        }
                        if ("GOODBYE".equals(utteranceId)) {
                            finishAffinity();
                        }
                    });
                }

                @Override public void onError(String utteranceId) {}
            });

            tts.speak(welcome, TextToSpeech.QUEUE_FLUSH, null, "WELCOME");
        }
    }

    private void startVoiceInput() {
        if (hasNavigated || isListening) return;
        isListening = true;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: Hospital or Emergency");
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        isListening = false;
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && !hasNavigated) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String command = results.get(0).toLowerCase();
                if (vibrator != null) vibrator.vibrate(300);
                handleVoiceCommand(command);
            } else {
                speakAndRetry();
            }
        } else {
            speakAndRetry();
        }
    }

    private void speakAndRetry() {
        if (hasNavigated) return;
        tts.speak("I did not catch that. Please say Hospital, Emergency, or Leave App.",
                TextToSpeech.QUEUE_FLUSH, null, "RETRY");
    }

    private void handleVoiceCommand(String command) {
        if (hasNavigated) return;

        if (command.contains("leave") || command.contains("exit")
                || command.contains("close")) {
            tts.speak("Goodbye " + userName + ". Take care.",
                    TextToSpeech.QUEUE_FLUSH, null, "GOODBYE");

        } else if (command.contains("hospital")) {
            hasNavigated = true;
            if (vibrator != null) vibrator.vibrate(400);
            new Handler().postDelayed(() ->
                    startActivity(new Intent(this, HospitalListActivity.class)), 1000);

        } else if (command.contains("emergency")) {
            hasNavigated = true;
            if (vibrator != null) vibrator.vibrate(400);
            new Handler().postDelayed(() ->
                    startActivity(new Intent(this, EmergencyActivity.class)), 1000);

        } else {
            speakAndRetry();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasNavigated = false;
        isListening = false;
        sensorManager.registerListener(shakeDetector,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(shakeDetector);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
package com.example.SeeForMeHealthApp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class WelcomeActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // Own TTS and SpeechRecognizer — same pattern as HospitalListActivity
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;
    private Handler handler = new Handler();

    private String spokenName   = "";
    private boolean isConfirming = false;

    private static final int PERMISSION_REQUEST_CODE = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Own TTS instance
        tts = new TextToSpeech(this, this);

        // Own SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }

    // ─── TTS INIT ─────────────────────────────────────────────────
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}

                @Override
                public void onDone(String id) {
                    // Same pattern as HospitalListActivity
                    if (id.equals("ASK_NAME") || id.equals("ASK_AGAIN")) {
                        runOnUiThread(() -> {
                            isConfirming = false;
                            handler.postDelayed(() -> startListening(), 1500);
                        });
                    } else if (id.equals("CONFIRM_NAME")) {
                        runOnUiThread(() -> {
                            isConfirming = true;
                            handler.postDelayed(() -> startListening(), 1500);
                        });
                    } else if (id.equals("GOODBYE")) {
                        runOnUiThread(() -> finishAffinity());
                    } else if (id.equals("EXPLAIN_PERMISSIONS")) {
                        runOnUiThread(() -> requestPermissions());
                    }
                }

                @Override public void onError(String id) {}
            });

            // Check if patient already registered
            SharedPreferences prefs =
                    getSharedPreferences("SeeForMePrefs", MODE_PRIVATE);
            String savedName = prefs.getString("patient_name", null);

            if (savedName != null && !savedName.isEmpty()) {
                // Already registered — go straight to patient screen
                Intent intent = new Intent(WelcomeActivity.this, PatientActivity.class);
                intent.putExtra("USER_NAME", savedName);
                startActivity(intent);
                finish();
            } else {
                if (hasAllPermissions()) {
                    speak("Welcome to See For Me Health. Please say your name.",
                            "ASK_NAME");
                } else {
                    speak("Welcome to See For Me Health. " +
                                    "This app needs microphone permission. " +
                                    "Please allow it when the popup appears.",
                            "EXPLAIN_PERMISSIONS");
                }
            }
        }
    }

    // ─── SPEAK HELPER ─────────────────────────────────────────────
    // Proper Bundle — same as HospitalListActivity
    private void speak(String text, String utteranceId) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
    }

    // ─── START LISTENING ──────────────────────────────────────────
    // Fresh Intent every time — same as HospitalListActivity
    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle b) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float v) {}

            @Override
            public void onBufferReceived(byte[] b) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        retryCurrentStep();
                    } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        handler.postDelayed(() -> startListening(), 1500);
                    } else {
                        handler.postDelayed(() -> startListening(), 1000);
                    }
                });
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    if (vibrator != null) vibrator.vibrate(300);

                    for (String match : matches) {
                        String heard = match.toLowerCase().trim();

                        // Leave command
                        if (heard.contains("leave") || heard.contains("exit")
                                || heard.contains("close") || heard.contains("quit")) {
                            speak("Goodbye. Take care.", "GOODBYE");
                            return;
                        }

                        if (!heard.isEmpty()) {
                            if (isConfirming) {
                                handleConfirmation(heard);
                            } else {
                                handleName(match);
                            }
                            return;
                        }
                    }
                    retryCurrentStep();
                } else {
                    retryCurrentStep();
                }
            }

            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int i, Bundle b) {}
        });

        speechRecognizer.startListening(intent);
    }

    // ─── RETRY ────────────────────────────────────────────────────
    private void retryCurrentStep() {
        if (isConfirming) {
            speak("I did not hear you. Please say yes or no.", "CONFIRM_NAME");
        } else {
            speak("I did not hear you. Please say your name.", "ASK_AGAIN");
        }
    }

    // ─── HANDLE NAME ──────────────────────────────────────────────
    private void handleName(String rawName) {
        spokenName = rawName.trim();

        if (spokenName.isEmpty()) {
            speak("I did not catch that. Please say your name.", "ASK_AGAIN");
            return;
        }

        // Capitalise first letter
        spokenName = spokenName.substring(0, 1).toUpperCase()
                + spokenName.substring(1).toLowerCase();

        if (vibrator != null) vibrator.vibrate(300);
        speak("Your name is " + spokenName +
                ". Say yes to confirm or no to try again.", "CONFIRM_NAME");
    }

    // ─── HANDLE CONFIRMATION ──────────────────────────────────────
    private void handleConfirmation(String answer) {
        if (answer.contains("yes") || answer.contains("yeah")
                || answer.contains("yep") || answer.contains("correct")
                || answer.contains("ok") || answer.contains("okay")) {

            if (vibrator != null) vibrator.vibrate(500);

            SharedPreferences prefs =
                    getSharedPreferences("SeeForMePrefs", MODE_PRIVATE);
            prefs.edit()
                    .putString("patient_name", spokenName)
                    .putString("patient_phone", "")
                    .apply();

            Intent intent = new Intent(WelcomeActivity.this, PatientActivity.class);
            intent.putExtra("USER_NAME", spokenName);
            startActivity(intent);
            finish();

        } else if (answer.contains("no") || answer.contains("nope")
                || answer.contains("nah") || answer.contains("wrong")) {
            isConfirming = false;
            speak("Okay. Please say your name again.", "ASK_AGAIN");

        } else {
            speak("Please say yes or no.", "CONFIRM_NAME");
        }
    }

    // ─── PERMISSIONS ──────────────────────────────────────────────
    private boolean hasAllPermissions() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CALL_PHONE
                },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            speak("Thank you. Please say your name.", "ASK_NAME");
        }
    }

    // ─── CLEANUP ──────────────────────────────────────────────────
    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) speechRecognizer.cancel();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
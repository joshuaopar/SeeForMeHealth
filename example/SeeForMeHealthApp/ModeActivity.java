package com.example.SeeForMeHealthApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Locale;

public class ModeActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private Vibrator vibrator;
    private Handler handler = new Handler();
    private boolean ttsReady = false;
    private boolean navigated = false;
    private static final int SPEECH_REQUEST_CODE = 100;

    private int devTapCount = 0;
    private long lastTapTime = 0;

    private static final String PREFS    = "SeeForMePrefs";
    private static final String KEY_DEV  = "is_developer";
    private static final String KEY_MODE = "selected_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        tts = new TextToSpeech(this, this);

        // Secret developer tap on title
        TextView tvTitle = findViewById(R.id.tv_app_title);
        if (tvTitle != null) {
            tvTitle.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 1500) {
                    devTapCount++;
                } else {
                    devTapCount = 1;
                }
                lastTapTime = now;
                if (devTapCount >= 5) {
                    devTapCount = 0;
                    toggleDeveloperMode();
                }
            });
        }
    }

    // ─── TTS INIT ─────────────────────────────────────────────────
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);
            ttsReady = true;

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}

                @Override
                public void onDone(String id) {
                    if (navigated) return;
                    if (id.startsWith("ask_") || id.startsWith("retry")) {
                        runOnUiThread(() ->
                                handler.postDelayed(() -> {
                                    if (!navigated) startVoiceInput();
                                }, 500)
                        );
                    }
                    if (id.equals("goodbye")) {
                        runOnUiThread(() -> finishAffinity());
                    }
                }

                @Override public void onError(String id) {}
            });

            speak("Are you a patient or hospital staff?", "ask_mode");
        }
    }

    // ─── SPEAK HELPER ─────────────────────────────────────────────
    private void speak(String text, String utteranceId) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
    }

    // ─── LAUNCH VOICE INPUT ───────────────────────────────────────
    private void startVoiceInput() {
        if (navigated) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: Patient or Staff");
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    // ─── HANDLE SPEECH RESULT ─────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                for (String match : results) {
                    String heard = match.toLowerCase().trim();
                    if (!heard.isEmpty() && handleCommand(heard)) return;
                }
            }
            speak("I did not catch that. Please say patient or staff.", "retry_mode");
        } else {
            speak("Please say patient or staff.", "retry_mode");
        }
    }

    // ─── HANDLE COMMAND ───────────────────────────────────────────
    private boolean handleCommand(String command) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean isDeveloper = prefs.getBoolean(KEY_DEV, false);

        if (command.contains("leave") || command.contains("exit")
                || command.contains("close") || command.contains("quit")) {
            speak("Goodbye. Take care.", "goodbye");
            return true;

        } else if (command.contains("patient")) {
            navigated = true;
            if (vibrator != null) vibrator.vibrate(400);
            if (!isDeveloper) {
                prefs.edit().putString(KEY_MODE, "patient").apply();
            }
            startActivity(new Intent(ModeActivity.this, WelcomeActivity.class));
            if (!isDeveloper) finish();
            return true;

        } else if (command.contains("staff") || command.contains("hospital")
                || command.contains("stuff") || command.contains("start")) {
            navigated = true;
            if (vibrator != null) vibrator.vibrate(400);
            if (!isDeveloper) {
                // Save staff mode
                prefs.edit().putString(KEY_MODE, "staff").apply();
            }
            // ── CHANGED — go to LOGIN screen first, not dashboard ──
            startActivity(new Intent(ModeActivity.this, StaffLoginActivity.class));
            if (!isDeveloper) finish();
            return true;
        }

        return false;
    }

    // ─── TOGGLE DEVELOPER MODE ────────────────────────────────────
    private void toggleDeveloperMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean currentlyDev = prefs.getBoolean(KEY_DEV, false);
        prefs.edit()
                .putBoolean(KEY_DEV, !currentlyDev)
                .remove(KEY_MODE)
                .remove("patient_name")
                .apply();
        if (vibrator != null) vibrator.vibrate(800);
        String msg = !currentlyDev
                ? "Developer mode ON — you can switch freely"
                : "Developer mode OFF — next choice will be permanent";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ─── LIFECYCLE ────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        navigated = false;
        if (ttsReady && !tts.isSpeaking()) {
            handler.postDelayed(() ->
                    speak("Are you a patient or hospital staff?", "ask_mode"), 800);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
package com.example.SeeForMeHealthApp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

        private TextToSpeech tts;
        private Handler handler = new Handler();

        private static final String PREFS = "SeeForMePrefs";
        private static final String KEY_MODE = "selected_mode";
        private static final String KEY_DEV = "is_developer";

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                try {
                        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
                } catch (Exception e) {}

                setContentView(R.layout.activity_main);

                SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                boolean isDeveloper = prefs.getBoolean(KEY_DEV, false);
                String savedMode = prefs.getString(KEY_MODE, null);

                if (isDeveloper) {
                        // Developer phone — always show mode selector
                        goToModeActivity();
                        return;
                }

                if ("patient".equals(savedMode)) {
                        warmUpTts();
                        startActivity(new Intent(this, WelcomeActivity.class));
                        finish();
                        return;
                } else if ("staff".equals(savedMode)) {
                        warmUpTts();
                        startActivity(new Intent(this, StaffDashboardActivity.class));
                        finish();
                        return;
                }

                // First time — speak welcome then go to ModeActivity
                tts = new TextToSpeech(this, this);
        }

        // ─── SPEAK HELPER ─────────────────────────────────────────────
        // Passes proper Bundle so onDone() fires on ALL Android devices
        private void speak(String text, String utteranceId) {
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);

                // Fallback — if onDone never fires, navigate anyway
                // 80ms per character + 3 seconds buffer
                long duration = (text.length() * 80L) + 3000L;
                handler.postDelayed(() -> goToModeActivity(), duration);
        }

        // ─── GO TO MODE ACTIVITY ──────────────────────────────────────
        private void goToModeActivity() {
                handler.removeCallbacksAndMessages(null);
                startActivity(new Intent(MainActivity.this, ModeActivity.class));
                finish();
        }

        // ─── WARM UP TTS ENGINE FOR NEXT SCREEN ──────────────────────
        private void warmUpTts() {
                new TextToSpeech(this, status -> {
                        // just creating + initializing this instance loads
                        // the system TTS service into memory early
                });
        }

        // ─── TTS INIT ─────────────────────────────────────────────────
        @Override
        public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                        int result = tts.setLanguage(Locale.ENGLISH);
                        if (result == TextToSpeech.LANG_MISSING_DATA
                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                tts.setLanguage(Locale.getDefault());
                        }

                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

                                @Override
                                public void onStart(String utteranceId) {}

                                @Override
                                public void onDone(String utteranceId) {
                                        runOnUiThread(() -> {
                                                if ("WELCOME".equals(utteranceId)) {
                                                        goToModeActivity();
                                                }
                                        });
                                }

                                @Override
                                public void onError(String utteranceId) {
                                        runOnUiThread(() -> goToModeActivity());
                                }
                        });

                        // FIXED — proper Bundle so onDone fires on Tecno Pop 8
                        speak("Welcome to See For Me Health.", "WELCOME");

                } else {
                        // TTS failed to init — skip straight to ModeActivity
                        goToModeActivity();
                }
        }

        // ─── CLEANUP ──────────────────────────────────────────────────
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
package com.example.SeeForMeHealthApp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class EmergencyActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isTtsDone = false;

    private static final String MULAGO_NUMBER = "tel:0789700389";
    private static final String RUBAGA_NUMBER = "tel:0764510800";
    private static final int CALL_PERMISSION_CODE = 1;
    private static final int MIC_PERMISSION_CODE = 2;

    private String pendingNumber = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(500);

        tts = new TextToSpeech(this, this);

        findViewById(R.id.btnCallMulago).setOnClickListener(v -> makeCall(MULAGO_NUMBER));
        findViewById(R.id.btnCallRubaga).setOnClickListener(v -> makeCall(RUBAGA_NUMBER));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_CODE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_CODE);
        }
    }

    private void makeCall(String number) {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) tts.stop();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingNumber = number;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_CODE);
            return;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse(number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
        } catch (SecurityException e) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse(number));
            startActivity(dialIntent);
        }
    }

    private void leaveApp() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) tts.stop();
        tts.speak("Goodbye. Take care.", TextToSpeech.QUEUE_FLUSH, null, null);
        new Handler().postDelayed(() -> finishAffinity(), 2500);
    }

    private void speakAndRetry() {
        if (tts != null) {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}

                @Override
                public void onDone(String id) {
                    if ("RETRY".equals(id)) {
                        runOnUiThread(() -> {
                            if (speechRecognizer != null)
                                speechRecognizer.startListening(recognizerIntent);
                        });
                    }
                }

                @Override public void onError(String id) {}
            });
            tts.speak("I did not catch that. Say Mulago, Rubaga, or Leave App.",
                    TextToSpeech.QUEUE_FLUSH, null, "RETRY");
        }
    }

    private void startVoiceListening() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    Log.d("VOICE", "Heard: " + matches.toString());
                    for (String phrase : matches) {
                        String word = phrase.toLowerCase().trim();

                        if (word.contains("leave") || word.contains("exit")
                                || word.contains("close")) {
                            leaveApp();
                            return;
                        }

                        if (word.contains("mulago") || word.startsWith("m")
                                || word.equals("em") || word.equals("am")) {
                            makeCall(MULAGO_NUMBER);
                            return;
                        }

                        if (word.contains("rubaga") || word.startsWith("r")
                                || word.equals("are") || word.equals("ar")) {
                            makeCall(RUBAGA_NUMBER);
                            return;
                        }
                    }
                    speakAndRetry();
                } else {
                    speakAndRetry();
                }
            }

            @Override
            public void onError(int error) {
                Log.d("VOICE", "Error: " + error);
                speakAndRetry();
            }

            @Override public void onReadyForSpeech(Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle p) {}
            @Override public void onEvent(int e, Bundle p) {}
        });

        speechRecognizer.startListening(recognizerIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MIC_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (isTtsDone) startVoiceListening();
        }

        if (requestCode == CALL_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingNumber != null) {
            makeCall(pendingNumber);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}

                @Override
                public void onDone(String id) {
                    isTtsDone = true;
                    runOnUiThread(() -> {
                        if (ContextCompat.checkSelfPermission(EmergencyActivity.this,
                                Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                            startVoiceListening();
                        }
                    });
                }

                @Override public void onError(String id) {}
            });

            tts.speak(
                    "Emergency mode activated. To call Mulago Hospital, say Mulago. " +
                            "To call Rubaga Hospital, say Rubaga. " +
                            "To leave the app, say leave app.",
                    TextToSpeech.QUEUE_FLUSH, null, "EMERGENCY_TTS"
            );
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
package com.example.SeeForMeHealthApp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class HospitalListActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // ────────────────────────────────────────────────────────────────────────
    // Fields
    // ────────────────────────────────────────────────────────────────────────

    private TextToSpeech     tts;
    private SpeechRecognizer speechRecognizer;
    private TextView         tvStatus;
    private final Handler    handler = new Handler();

    // ── Guards ───────────────────────────────────────────────────────────────
    private volatile boolean bookingComplete  = false;
    private volatile boolean isSpeaking       = false;
    private volatile boolean isListening      = false;   // NEW: track mic state

    // ── Steps ────────────────────────────────────────────────────────────────
    private static final int STEP_CHOOSE_SPECIALITY = 0;
    private static final int STEP_CHOOSE_HOSPITAL   = 1;
    private static final int STEP_GET_NAME          = 2;
    private static final int STEP_GET_PHONE         = 3;
    private static final int STEP_CONFIRM_PHONE     = 4;
    private static final int STEP_GET_LOCATION      = 5;
    private static final int STEP_CHOOSE_DATE       = 6;
    private static final int STEP_CHOOSE_TIME       = 7;

    private int currentStep = STEP_CHOOSE_SPECIALITY;
    private int retryCount  = 0;

    // ── Patient data ─────────────────────────────────────────────────────────
    private String chosenHospital   = "";
    private String chosenSpeciality = "";
    private String chosenDate       = "";
    private String patientName      = "";
    private String patientPhone     = "";
    private String patientLocation  = "";

    // ── TTS utterance IDs that must open the mic when TTS finishes ───────────
    private static final Set<String> LISTEN_AFTER = new HashSet<>(Arrays.asList(
            "ask_speciality", "ask_hospital", "ask_name", "ask_phone",
            "ask_location",   "ask_date",     "ask_time",
            "confirm_phone",  "phone_saved",  "name_confirmed",
            "location_confirmed", "confirm_hospital", "confirm_spec", "retry"
    ));

    // ── Word-boundary patterns for digit matching (avoids "bone" → "one") ────
    private static final Pattern P1 = Pattern.compile("\\b(1|one|first)\\b");
    private static final Pattern P2 = Pattern.compile("\\b(2|two|second)\\b");
    private static final Pattern P3 = Pattern.compile("\\b(3|three|third)\\b");
    private static final Pattern P4 = Pattern.compile("\\b(4|four|fourth)\\b");
    private static final Pattern P5 = Pattern.compile("\\b(5|five|fifth)\\b");
    private static final Pattern P6 = Pattern.compile("\\b(6|six|sixth)\\b");
    private static final Pattern P7 = Pattern.compile("\\b(7|seven|seventh)\\b");
    private static final Pattern P8 = Pattern.compile("\\b(8|eight|eighth)\\b");
    private static final Pattern P9 = Pattern.compile("\\b(9|nine|ninth)\\b");

    // ────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_list);
        tvStatus = findViewById(R.id.tv_status);
        tts = new TextToSpeech(this, this);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            updateStatus("TTS failed to initialise.");
            return;
        }
        tts.setLanguage(Locale.ENGLISH);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onStart(String id) {
                isSpeaking = true;
            }

            @Override
            public void onDone(String id) {
                isSpeaking = false;
                if (bookingComplete) return;

                if (LISTEN_AFTER.contains(id) || id.startsWith("read_")) {
                    // Give TTS engine a moment to fully release the audio focus
                    // before opening the mic (1 200 ms is reliable on most devices)
                    runOnUiThread(() ->
                            handler.postDelayed(() -> {
                                if (!bookingComplete && !isSpeaking && !isListening) {
                                    startListening();
                                }
                            }, 1200)
                    );
                }
            }

            @Override
            public void onError(String id) {
                isSpeaking = false;
            }
        });

        // Kick off the first step
        askSpeciality();
    }

    @Override
    protected void onDestroy() {
        stopEverything();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }

    // ────────────────────────────────────────────────────────────────────────
    // TTS helper
    // ────────────────────────────────────────────────────────────────────────

    private void speak(String text, String utteranceId) {
        if (bookingComplete || tts == null) return;
        isSpeaking = true;
        // Cancel any pending mic start before speaking
        safeCancelListening();
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Flow helpers
    // ────────────────────────────────────────────────────────────────────────

    /** Stops TTS and recogniser; marks the session as done. */
    private void stopEverything() {
        bookingComplete = true;
        isSpeaking      = false;
        isListening     = false;
        handler.removeCallbacksAndMessages(null);
        safeCancelListening();
        if (tts != null) tts.stop();
    }

    /** Cancel the recogniser without throwing if it is not active. */
    private void safeCancelListening() {
        isListening = false;
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            }
        } catch (Exception ignored) {}
    }

    private void leaveApp() {
        stopEverything();
        if (tts != null) {
            tts.speak("Goodbye. Take care.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
        handler.postDelayed(this::finishAffinity, 2500);
    }

    /**
     * Schedule the next conversational step after the current TTS phrase
     * has had enough time to be spoken + the mic warmup delay.
     */
    private void advanceTo(Runnable nextStep, long delayMs) {
        handler.postDelayed(() -> {
            if (!bookingComplete) nextStep.run();
        }, delayMs);
    }

    // ── Retry logic ──────────────────────────────────────────────────────────

    /** Called when the user's speech could not be matched. */
    private void noMatch() {
        if (bookingComplete) return;
        retryCount++;
        if (retryCount >= 3) {
            // After 3 consecutive failures, re-read the full prompt for this step
            retryCount = 0;
            retryCurrentStep();
        } else {
            speak("I did not catch that. Please try again.", "retry");
        }
    }

    /** Re-speaks the prompt for the current step (does NOT reset retryCount here). */
    private void retryCurrentStep() {
        switch (currentStep) {
            case STEP_CHOOSE_SPECIALITY: askSpeciality();  break;
            case STEP_CHOOSE_HOSPITAL:   askHospital();    break;
            case STEP_GET_NAME:          askForName();     break;
            case STEP_GET_PHONE:         askForPhone();    break;
            case STEP_CONFIRM_PHONE:     confirmPhone();   break;
            case STEP_GET_LOCATION:      askForLocation(); break;
            case STEP_CHOOSE_DATE:       askForDate();     break;
            case STEP_CHOOSE_TIME:       askForTime();     break;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Step prompts
    // ────────────────────────────────────────────────────────────────────────

    private void askSpeciality() {
        currentStep = STEP_CHOOSE_SPECIALITY;
        retryCount  = 0;
        updateStatus("Choose a speciality...");
        speak(
                "The available specialities are: " +
                        "Say 1 or General for General. " +
                        "Say 2 or Maternity for Maternity. " +
                        "Say 3 or Orthopedic for Orthopedic. " +
                        "Say 4 or Dental for Dental. " +
                        "Say 5 or Eye for Eye. " +
                        "Say 6 or Cardiology for Cardiology. " +
                        "Say 7 or Pediatrics for Pediatrics. " +
                        "Say 8 or Neurology for Neurology. " +
                        "Say 9 or Gynecology for Gynecology. " +
                        "Which would you like?",
                "ask_speciality"
        );
    }

    private void askHospital() {
        currentStep = STEP_CHOOSE_HOSPITAL;
        retryCount  = 0;
        updateStatus("Choose a hospital...");
        speak(
                "Which hospital do you want? " +
                        "Say 1 or Mulago for Mulago National Referral Hospital. " +
                        "Say 2 or Mengo for Mengo Hospital.",
                "ask_hospital"
        );
    }

    private void askForName() {
        currentStep = STEP_GET_NAME;
        retryCount  = 0;
        updateStatus("Listening for your name...");
        speak("Please say your full name.", "ask_name");
    }

    private void askForPhone() {
        currentStep = STEP_GET_PHONE;
        retryCount  = 0;
        updateStatus("Listening for your phone number...");
        speak(
                "Please say your phone number digit by digit. " +
                        "For example: zero seven seven two, three four five, six seven eight.",
                "ask_phone"
        );
    }

    private void confirmPhone() {
        currentStep = STEP_CONFIRM_PHONE;
        retryCount  = 0;
        updateStatus("Confirm your phone number...");
        String spaced = formatPhoneForSpeech(patientPhone);
        speak(
                "I have your number as " + spaced +
                        ". Say yes to confirm or no to say it again.",
                "confirm_phone"
        );
    }

    private void askForLocation() {
        currentStep = STEP_GET_LOCATION;
        retryCount  = 0;
        updateStatus("Listening for your location...");
        speak(
                "Please say your current location or where you are coming from.",
                "ask_location"
        );
    }

    private void askForDate() {
        currentStep = STEP_CHOOSE_DATE;
        retryCount  = 0;
        updateStatus("Listening for preferred date...");
        speak(
                "What date would you like your appointment? " +
                        "Say something like Monday or June tenth.",
                "ask_date"
        );
    }

    private void askForTime() {
        currentStep = STEP_CHOOSE_TIME;
        retryCount  = 0;
        updateStatus("Listening for preferred time...");
        speak(
                "What time do you prefer? Say something like ten A M or two P M.",
                "ask_time"
        );
    }

    // ────────────────────────────────────────────────────────────────────────
    // Digit / phone helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Converts recognised speech into a clean digit string.
     * Word-numbers are replaced before stripping non-digit characters.
     */
    private String speechToDigits(String heard) {
        String s = heard.toLowerCase().trim();

        // Multi-word tokens first
        s = s.replace("double zero", "00")
                .replace("double oh",   "00")
                .replace("zero",        "0")
                .replace("oh",          "0")
                .replace("one",         "1")
                .replace("two",         "2")
                .replace("three",       "3")
                .replace("four",        "4")
                .replace("five",        "5")
                .replace("six",         "6")
                .replace("seven",       "7")
                .replace("eight",       "8")
                .replace("nine",        "9");

        boolean hasPlus = s.contains("+");
        String  digits  = s.replaceAll("[^0-9]", "");
        return hasPlus ? "+" + digits : digits;
    }

    /** Groups a digit string into naturally spoken groups for TTS readback. */
    private String formatPhoneForSpeech(String number) {
        boolean intl   = number.startsWith("+");
        String  digits = intl ? number.substring(1) : number;

        StringBuilder sb = new StringBuilder();
        if (intl) sb.append("plus ");

        int len        = digits.length();
        int i          = 0;
        int firstGroup = (len > 7 && (len - 4) % 3 == 0) ? 4 : 3;
        int end        = Math.min(firstGroup, len);

        for (; i < end; i++) sb.append(digits.charAt(i)).append(" ");

        while (i < len) {
            sb.append(", ");
            int groupEnd = Math.min(i + 3, len);
            for (int j = i; j < groupEnd; j++) sb.append(digits.charAt(j)).append(" ");
            i = groupEnd;
        }
        return sb.toString().trim();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Word-boundary number matching (FIXED — no more substring false-positives)
    // ────────────────────────────────────────────────────────────────────────

    private boolean matchesNumber(Pattern p, String heard) {
        return p.matcher(heard).find();
    }

    /**
     * Returns the speciality name for a recognised utterance, or null if
     * no clear number/name was found.
     * Also accepts the speciality name spoken directly.
     */
    private String matchSpeciality(String heard) {
        // Direct name match (highest confidence)
        if (heard.contains("general"))     return "General";
        if (heard.contains("matern"))      return "Maternity";
        if (heard.contains("ortho"))       return "Orthopedic";
        if (heard.contains("dent"))        return "Dental";
        if (heard.contains("eye")
                || heard.contains("ophthal"))     return "Eye";
        if (heard.contains("cardio"))      return "Cardiology";
        if (heard.contains("pediatr")
                || heard.contains("paediatr"))    return "Pediatrics";
        if (heard.contains("neuro"))       return "Neurology";
        if (heard.contains("gynae")
                || heard.contains("gyneco"))      return "Gynecology";

        // Number match with word-boundary patterns
        if (matchesNumber(P1, heard)) return "General";
        if (matchesNumber(P2, heard)) return "Maternity";
        if (matchesNumber(P3, heard)) return "Orthopedic";
        if (matchesNumber(P4, heard)) return "Dental";
        if (matchesNumber(P5, heard)) return "Eye";
        if (matchesNumber(P6, heard)) return "Cardiology";
        if (matchesNumber(P7, heard)) return "Pediatrics";
        if (matchesNumber(P8, heard)) return "Neurology";
        if (matchesNumber(P9, heard)) return "Gynecology";

        return null;
    }

    /**
     * Returns "mulago", "mengo", or null.
     * Uses word-boundary patterns for numeric choices.
     */
    private String matchHospital(String heard) {
        // Direct name match
        if (heard.contains("mulago")) return "mulago";
        if (heard.contains("mengo"))  return "mengo";

        // Numeric choice
        if (matchesNumber(P1, heard)) return "mulago";
        if (matchesNumber(P2, heard)) return "mengo";

        return null;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Core match-and-transition logic
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Tries to match the heard utterance to the current step.
     *
     * @return true  → utterance was consumed (even if it was a retry prompt)
     *         false → no match found
     */
    private boolean tryMatch(String heard) {
        if (bookingComplete) return true;

        // ── Global exit commands ─────────────────────────────────────────────
        if (heard.contains("leave") || heard.contains("exit")
                || heard.contains("close") || heard.contains("quit")) {
            leaveApp();
            return true;
        }

        switch (currentStep) {

            // ── Choose Speciality ───────────────────────────────────────────
            case STEP_CHOOSE_SPECIALITY: {
                String spec = matchSpeciality(heard);
                if (spec != null) {
                    retryCount       = 0;
                    chosenSpeciality = spec;
                    speak("You chose " + spec + ".", "confirm_spec");
                    advanceTo(this::askHospital, 2500);
                    return true;
                }
                return false;
            }

            // ── Choose Hospital ─────────────────────────────────────────────
            case STEP_CHOOSE_HOSPITAL: {
                String hospital = matchHospital(heard);
                if (hospital != null) {
                    retryCount     = 0;
                    chosenHospital = hospital;
                    String fullName = hospital.equals("mulago")
                            ? "Mulago National Referral Hospital"
                            : "Mengo Hospital";
                    speak("You chose " + fullName + ".", "confirm_hospital");
                    advanceTo(this::askForName, 3000);
                    return true;
                }
                return false;
            }

            // ── Get Name ────────────────────────────────────────────────────
            case STEP_GET_NAME: {
                String trimmed = heard.trim();
                if (!trimmed.isEmpty()) {
                    retryCount  = 0;
                    patientName = toTitleCase(trimmed);
                    speak("Thank you, " + patientName + ".", "name_confirmed");
                    advanceTo(this::askForPhone, 2500);
                    return true;
                }
                return false;
            }

            // ── Get Phone ───────────────────────────────────────────────────
            case STEP_GET_PHONE: {
                String trimmed = heard.trim();
                if (!trimmed.isEmpty()) {
                    retryCount = 0;
                    String digits = speechToDigits(trimmed);
                    int digitCount = digits.replaceAll("[^0-9]", "").length();

                    if (digitCount >= 7) {
                        patientPhone = digits;
                        // Short delay so the user's voice echo dies away
                        advanceTo(this::confirmPhone, 600);
                    } else {
                        speak(
                                "Sorry, I only caught " + digitCount + " digits. " +
                                        "Please say each digit slowly, one at a time.",
                                "retry"
                        );
                    }
                    return true;
                }
                return false;
            }

            // ── Confirm Phone ───────────────────────────────────────────────
            case STEP_CONFIRM_PHONE: {
                if (heard.contains("yes") || heard.contains("yeah")
                        || heard.contains("yep") || heard.contains("correct")
                        || heard.contains("right") || heard.contains("confirm")
                        || heard.contains("okay") || heard.contains("ok")) {
                    retryCount = 0;
                    speak("Perfect. Your number has been saved.", "phone_saved");
                    advanceTo(this::askForLocation, 2500);
                    return true;
                }
                if (heard.contains("no")    || heard.contains("nope")
                        || heard.contains("wrong") || heard.contains("incorrect")
                        || heard.contains("change") || heard.contains("again")) {
                    retryCount   = 0;
                    patientPhone = "";
                    speak("No problem. Let us try again.", "retry");
                    advanceTo(this::askForPhone, 2000);
                    return true;
                }
                return false;
            }

            // ── Get Location ────────────────────────────────────────────────
            case STEP_GET_LOCATION: {
                String trimmed = heard.trim();
                if (!trimmed.isEmpty()) {
                    retryCount      = 0;
                    patientLocation = toTitleCase(trimmed);
                    speak("Thank you. Your location is " + patientLocation + ".", "location_confirmed");
                    advanceTo(this::askForDate, 2800);
                    return true;
                }
                return false;
            }

            // ── Choose Date ─────────────────────────────────────────────────
            case STEP_CHOOSE_DATE: {
                String trimmed = heard.trim();
                if (!trimmed.isEmpty()) {
                    retryCount = 0;
                    chosenDate = trimmed;
                    speak("You said " + chosenDate + ".", "confirm_spec"); // reuses listen-after id
                    advanceTo(this::askForTime, 2000);
                    return true;
                }
                return false;
            }

            // ── Choose Time ─────────────────────────────────────────────────
            case STEP_CHOOSE_TIME: {
                String trimmed = heard.trim();
                if (!trimmed.isEmpty()) {
                    retryCount = 0;
                    proceedToBooking(trimmed);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Booking hand-off
    // ────────────────────────────────────────────────────────────────────────

    private void proceedToBooking(String time) {
        stopEverything();

        // Persist reusable patient details
        getSharedPreferences("SeeForMePrefs", MODE_PRIVATE)
                .edit()
                .putString("patient_name",     patientName)
                .putString("patient_phone",     patientPhone)
                .putString("patient_location",  patientLocation)
                .apply();

        Intent intent = new Intent(this, PatientBookingActivity.class);
        intent.putExtra("hospital",   chosenHospital);
        intent.putExtra("speciality", chosenSpeciality);
        intent.putExtra("date",       chosenDate);
        intent.putExtra("time",       time);
        intent.putExtra("location",   patientLocation);
        startActivity(intent);
        finish();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Speech recognition
    // ────────────────────────────────────────────────────────────────────────

    private void startListening() {
        if (bookingComplete || isSpeaking || isListening || speechRecognizer == null) return;

        // Always cancel before re-starting to avoid ERROR_RECOGNIZER_BUSY
        try {
            speechRecognizer.cancel();
        } catch (Exception ignored) {}

        isListening = true;
        updateStatus("Listening… speak now");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        // Give the user up to 5 s of silence before auto-stopping
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,         5000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        // Minimum speech to avoid phantom triggers
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle b)      { updateStatus("Listening… speak now"); }
            @Override public void onBeginningOfSpeech()            { updateStatus("Hearing you…"); }
            @Override public void onRmsChanged(float v)            {}
            @Override public void onBufferReceived(byte[] b)       {}
            @Override public void onPartialResults(Bundle b)       {}
            @Override public void onEvent(int i, Bundle b)         {}
            @Override public void onEndOfSpeech()                  { updateStatus("Processing…"); }

            @Override
            public void onError(int error) {
                if (bookingComplete) return;
                isListening = false;
                runOnUiThread(() -> {
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            // Treat silence / no-match as a failed attempt
                            noMatch();
                            break;

                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            // Wait longer before retrying when the engine is busy
                            handler.postDelayed(() -> {
                                if (!bookingComplete && !isSpeaking) startListening();
                            }, 2000);
                            break;

                        case SpeechRecognizer.ERROR_AUDIO:
                        case SpeechRecognizer.ERROR_CLIENT:
                        default:
                            handler.postDelayed(() -> {
                                if (!bookingComplete && !isSpeaking) startListening();
                            }, 1200);
                            break;
                    }
                });
            }

            @Override
            public void onResults(Bundle results) {
                if (bookingComplete) return;
                isListening = false;

                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches == null || matches.isEmpty()) {
                    noMatch();
                    return;
                }

                // Try each hypothesis in confidence order (index 0 = best)
                for (String match : matches) {
                    String heard = match.toLowerCase().trim();
                    if (!heard.isEmpty() && tryMatch(heard)) return;
                }

                // None of the hypotheses matched
                noMatch();
            }
        });

        speechRecognizer.startListening(intent);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utilities
    // ────────────────────────────────────────────────────────────────────────

    /** Capitalises the first letter of every word. */
    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        String[]      words = input.split("\\s+");
        StringBuilder sb    = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    /** Thread-safe status bar update. */
    private void updateStatus(String msg) {
        runOnUiThread(() -> { if (tvStatus != null) tvStatus.setText(msg); });
    }
}
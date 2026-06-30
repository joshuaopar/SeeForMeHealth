package com.example.SeeForMeHealthApp;

import android.app.Application;
import android.content.SharedPreferences;

public class SeeForMeHealthApp extends Application {

    private static SeeForMeHealthApp instance;
    private SharedPreferences sharedPreferences;

    public static final String PREFS_NAME    = "SeeForMePrefs";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // TTS removed — each activity creates its own TTS instance
    }

    public static SeeForMeHealthApp getInstance() {
        return instance;
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, "");
    }
}
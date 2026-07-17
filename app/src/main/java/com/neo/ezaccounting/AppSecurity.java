package com.neo.ezaccounting;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AppSecurity {
    public static final String MODE_NONE = "none";
    public static final String MODE_BIOMETRIC = "biometric";
    public static final String MODE_PIN = "pin";
    public static final String MODE_PATTERN = "pattern";

    private static final String PREFS = "ez_app_security";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SALT = "secret_salt";
    private static final String KEY_HASH = "secret_hash";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;

    private AppSecurity() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getMode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_NONE);
    }

    public static boolean isEnabled(Context context) {
        return !MODE_NONE.equals(getMode(context));
    }

    public static String getModeLabel(Context context) {
        switch (getMode(context)) {
            case MODE_BIOMETRIC: return "指纹或面容";
            case MODE_PIN: return "四位数字密码";
            case MODE_PATTERN: return "九宫格图形锁";
            default: return "未启用";
        }
    }

    public static void setBiometric(Context context) {
        prefs(context).edit().putString(KEY_MODE, MODE_BIOMETRIC)
                .remove(KEY_SALT).remove(KEY_HASH).apply();
    }

    public static void setPin(Context context, String pin) {
        saveSecret(context, MODE_PIN, pin);
    }

    public static void setPattern(Context context, String pattern) {
        saveSecret(context, MODE_PATTERN, pattern);
    }

    public static void disable(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static boolean verifySecret(Context context, String candidate) {
        SharedPreferences preferences = prefs(context);
        String saltBase64 = preferences.getString(KEY_SALT, "");
        String hashBase64 = preferences.getString(KEY_HASH, "");
        if (saltBase64.isEmpty() || hashBase64.isEmpty() || candidate == null) return false;
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashBase64, Base64.NO_WRAP);
            return MessageDigest.isEqual(expected, derive(candidate, salt));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void saveSecret(Context context, String mode, String secret) {
        if (secret == null || secret.isEmpty()) throw new IllegalArgumentException("Secret cannot be empty");
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(secret, salt);
            prefs(context).edit()
                    .putString(KEY_MODE, mode)
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to save app lock", error);
        }
    }

    private static byte[] derive(String secret, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}

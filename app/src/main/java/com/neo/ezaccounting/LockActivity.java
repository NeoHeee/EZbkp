package com.neo.ezaccounting;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public class LockActivity extends FragmentActivity {
    public static final String EXTRA_REASON = "reason";

    private final Handler lockoutHandler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private TextView message;
    private EditText pinInput;
    private Button unlockButton;
    private PatternLockView patternView;
    private Button biometricRetryButton;
    private Button biometricExitButton;
    private BiometricPrompt biometricPrompt;
    private String normalPrompt = "请完成安全验证";

    private final Runnable lockoutTick = new Runnable() {
        @Override
        public void run() {
            refreshLockoutState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiTheme.applySystemBars(this);
        setFinishOnTouchOutside(false);
        buildBaseLayout();
        String mode = AppSecurity.getMode(this);
        if (AppSecurity.MODE_NONE.equals(mode)) { unlockSuccess(); return; }
        switch (mode) {
            case AppSecurity.MODE_BIOMETRIC: showBiometricLock(); break;
            case AppSecurity.MODE_PIN: showPinLock(); break;
            case AppSecurity.MODE_PATTERN: showPatternLock(); break;
            default: finishCanceled();
        }
    }

    private void buildBaseLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(42), dp(28), dp(28));
        root.setBackgroundColor(UiTheme.background(this));

        TextView logo = new TextView(this);
        logo.setText("¥✓");
        logo.setTextSize(28);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable logoBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(14, 165, 164)});
        logoBg.setCornerRadius(dp(22));
        logo.setBackground(logoBg);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        logoParams.bottomMargin = dp(22);
        root.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("验证后进入 EZ记账");
        title.setTextSize(23);
        title.setTextColor(UiTheme.primaryText(this));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap(dp(8)));

        message = new TextView(this);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        message.setText(reason == null || reason.trim().isEmpty() ? normalPrompt : reason);
        message.setTextSize(14.5f);
        message.setTextColor(UiTheme.secondaryText(this));
        message.setGravity(Gravity.CENTER);
        root.addView(message, fullWrap(dp(24)));
        setContentView(root);
    }

    private void showPinLock() {
        normalPrompt = "请输入四位数字密码";
        message.setText(normalPrompt);
        pinInput = new EditText(this);
        pinInput.setSingleLine(true);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setTextSize(25);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pinInput.setHint("••••");
        pinInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        pinInput.setBackground(roundedBox(UiTheme.surface(this), UiTheme.border(this), 14));
        root.addView(pinInput, fullWrap(dp(16)));

        unlockButton = primaryButton("解锁");
        root.addView(unlockButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        unlockButton.setOnClickListener(v -> verifyPin());

        pinInput.requestFocus();
        pinInput.postDelayed(() -> {
            if (!pinInput.isEnabled()) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(pinInput, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
        refreshLockoutState();
    }

    private void verifyPin() {
        if (AppSecurity.getRemainingLockoutMs(this) > 0) {
            refreshLockoutState();
            return;
        }
        String pin = pinInput.getText().toString();
        if (pin.length() != 4) { pinInput.setError("请输入四位数字密码"); return; }
        if (AppSecurity.verifySecret(this, pin)) {
            unlockSuccess();
            return;
        }
        pinInput.setText("");
        long delay = AppSecurity.recordFailedAttempt(this);
        if (delay > 0) refreshLockoutState();
        else message.setText("密码错误");
        pinInput.requestFocus();
    }

    private void showPatternLock() {
        normalPrompt = "请绘制九宫格图形";
        message.setText(normalPrompt);
        patternView = new PatternLockView(this);
        LinearLayout.LayoutParams patternParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        patternParams.bottomMargin = dp(10);
        root.addView(patternView, patternParams);

        TextView hint = new TextView(this);
        hint.setText("至少连接四个点");
        hint.setTextSize(13);
        hint.setTextColor(UiTheme.tertiaryText(this));
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, fullWrap(dp(6)));

        patternView.setListener(pattern -> {
            if (AppSecurity.getRemainingLockoutMs(this) > 0) {
                patternView.clearPattern();
                refreshLockoutState();
                return;
            }
            if (AppSecurity.verifySecret(this, pattern)) {
                unlockSuccess();
                return;
            }
            long delay = AppSecurity.recordFailedAttempt(this);
            if (delay > 0) refreshLockoutState();
            else message.setText("图形错误");
            patternView.postDelayed(patternView::clearPattern, 450);
        });
        refreshLockoutState();
    }

    private void refreshLockoutState() {
        lockoutHandler.removeCallbacks(lockoutTick);
        long remaining = AppSecurity.getRemainingLockoutMs(this);
        boolean lockedOut = remaining > 0;
        if (pinInput != null) pinInput.setEnabled(!lockedOut);
        if (unlockButton != null) unlockButton.setEnabled(!lockedOut);
        if (patternView != null) patternView.setEnabled(!lockedOut);

        if (!lockedOut) {
            message.setText(normalPrompt);
            return;
        }

        long seconds = Math.max(1L, (remaining + 999L) / 1000L);
        message.setText("尝试次数过多，请在 " + seconds + " 秒后重试");
        lockoutHandler.postDelayed(lockoutTick, Math.min(1000L, remaining));
    }

    private void showBiometricLock() {
        normalPrompt = "请使用指纹、面容或手机锁屏密码验证";
        message.setText(normalPrompt);

        int biometricAvailability = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (biometricAvailability != BiometricManager.BIOMETRIC_SUCCESS &&
                !isDeviceCredentialAvailable()) {
            message.setText("设备当前没有可用的生物识别或手机锁屏密码");
            addBiometricExitButton();
            return;
        }

        biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        unlockSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        message.setText("未识别，可重试或改用手机锁屏密码");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED) {
                            finishCanceled();
                            return;
                        }

                        if (errorCode == BiometricPrompt.ERROR_LOCKOUT) {
                            showBiometricRecovery(
                                    "指纹暂时被系统锁定。可使用手机锁屏密码验证，或稍后重新尝试指纹。");
                            return;
                        }

                        if (errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                            showBiometricRecovery(
                                    "指纹已被系统锁定。请使用手机锁屏密码完成一次验证后再使用指纹。");
                            return;
                        }

                        if (errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE) {
                            showBiometricRecovery("生物识别硬件暂时不可用，可改用手机锁屏密码验证。");
                            return;
                        }

                        showBiometricRecovery(errString.toString());
                    }
                });

        authenticateBiometricOrDeviceCredential();
    }

    @SuppressWarnings("deprecation")
    private void authenticateBiometricOrDeviceCredential() {
        clearBiometricRecovery();
        message.setText(normalPrompt);

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁 EZ记账")
                .setSubtitle("使用指纹、面容或手机锁屏密码")
                .setConfirmationRequired(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK |
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }

        try {
            biometricPrompt.authenticate(builder.build());
        } catch (IllegalArgumentException error) {
            showBiometricRecovery("系统验证组件暂时不可用，请返回后重试。");
        }
    }

    private boolean isDeviceCredentialAvailable() {
        KeyguardManager keyguardManager =
                (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isDeviceSecure();
    }

    private void showBiometricRecovery(String text) {
        message.setText(text);
        if (biometricRetryButton == null) {
            biometricRetryButton = primaryButton("重新验证（可用手机锁屏密码）");
            root.addView(biometricRetryButton,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            biometricRetryButton.setOnClickListener(v -> authenticateBiometricOrDeviceCredential());
        } else {
            biometricRetryButton.setVisibility(View.VISIBLE);
            biometricRetryButton.setEnabled(true);
        }
        addBiometricExitButton();
    }

    private void addBiometricExitButton() {
        if (biometricExitButton == null) {
            biometricExitButton = secondaryButton("退出应用");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            params.topMargin = dp(12);
            root.addView(biometricExitButton, params);
            biometricExitButton.setOnClickListener(v -> finishCanceled());
        } else {
            biometricExitButton.setVisibility(View.VISIBLE);
        }
    }

    private void clearBiometricRecovery() {
        if (biometricRetryButton != null) biometricRetryButton.setVisibility(View.GONE);
        if (biometricExitButton != null) biometricExitButton.setVisibility(View.GONE);
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(15, 118, 110), Color.rgb(13, 148, 136)});
        background.setCornerRadius(dp(14));
        button.setBackground(background);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(UiTheme.primaryText(this));
        button.setAllCaps(false);
        button.setBackground(roundedBox(UiTheme.surface(this), UiTheme.border(this), 14));
        return button;
    }

    private GradientDrawable roundedBox(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams fullWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private void unlockSuccess() {
        AppSecurity.resetFailedAttempts(this);
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void finishCanceled() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() { finishCanceled(); }

    @Override
    protected void onDestroy() {
        lockoutHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

package com.neo.ezaccounting;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
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

    private LinearLayout root;
    private TextView message;
    private int failedAttempts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        root.setBackgroundColor(Color.rgb(245, 248, 248));

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
        title.setTextColor(Color.rgb(20, 35, 35));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWrap(dp(8)));

        message = new TextView(this);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        message.setText(reason == null || reason.trim().isEmpty() ? "请完成安全验证" : reason);
        message.setTextSize(14.5f);
        message.setTextColor(Color.rgb(90, 105, 105));
        message.setGravity(Gravity.CENTER);
        root.addView(message, fullWrap(dp(24)));
        setContentView(root);
    }

    private void showPinLock() {
        message.setText("请输入四位数字密码");
        EditText pinInput = new EditText(this);
        pinInput.setSingleLine(true);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setTextSize(25);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pinInput.setHint("••••");
        pinInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        pinInput.setBackground(roundedBox(Color.WHITE, Color.rgb(196, 210, 208), 14));
        root.addView(pinInput, fullWrap(dp(16)));

        Button unlock = primaryButton("解锁");
        root.addView(unlock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        unlock.setOnClickListener(v -> {
            String pin = pinInput.getText().toString();
            if (pin.length() != 4) { pinInput.setError("请输入四位数字密码"); return; }
            if (AppSecurity.verifySecret(this, pin)) {
                unlockSuccess();
            } else {
                failedAttempts++;
                pinInput.setText("");
                message.setText(failedAttempts >= 3 ? "密码错误，请确认后重试" : "密码错误");
                pinInput.requestFocus();
            }
        });
        pinInput.requestFocus();
        pinInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(pinInput, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private void showPatternLock() {
        message.setText("请绘制九宫格图形");
        PatternLockView patternView = new PatternLockView(this);
        LinearLayout.LayoutParams patternParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        patternParams.bottomMargin = dp(10);
        root.addView(patternView, patternParams);

        TextView hint = new TextView(this);
        hint.setText("至少连接四个点");
        hint.setTextSize(13);
        hint.setTextColor(Color.rgb(105, 120, 120));
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, fullWrap(dp(6)));

        patternView.setListener(pattern -> {
            if (AppSecurity.verifySecret(this, pattern)) {
                unlockSuccess();
            } else {
                failedAttempts++;
                message.setText(failedAttempts >= 3 ? "图形错误，请确认后重试" : "图形错误");
                patternView.postDelayed(patternView::clearPattern, 450);
            }
        });
    }

    private void showBiometricLock() {
        message.setText("请使用系统生物识别验证");
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK;
        int availability = BiometricManager.from(this).canAuthenticate(authenticators);
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            message.setText("设备当前无法使用生物识别，请先在系统设置中录入指纹或面容");
            Button close = primaryButton("关闭");
            root.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            close.setOnClickListener(v -> finishCanceled());
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result); unlockSuccess();
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed(); message.setText("未识别，请重试");
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_CANCELED) finishCanceled();
                        else message.setText(errString);
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁 EZ记账")
                .setSubtitle("验证身份后进入应用")
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("取消")
                .setConfirmationRequired(false)
                .build();
        prompt.authenticate(info);
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

    private void unlockSuccess() { setResult(Activity.RESULT_OK); finish(); }
    private void finishCanceled() { setResult(Activity.RESULT_CANCELED); finish(); }

    @Override
    public void onBackPressed() { finishCanceled(); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

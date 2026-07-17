package com.neo.ezaccounting;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public class SecuritySettingsActivity extends FragmentActivity {
    private TextView currentMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showSettings();
    }

    private void showSettings() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 248, 248));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("进入 App 的安全验证");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 35, 35));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, fullWrap(dp(8)));

        TextView description = new TextView(this);
        description.setText("选择进入 EZ记账 时使用的验证方式。启用后，冷启动以及应用在后台停留一段时间后返回，都需要重新验证。");
        description.setTextSize(14.5f);
        description.setTextColor(Color.rgb(80, 95, 95));
        description.setLineSpacing(0, 1.15f);
        root.addView(description, fullWrap(dp(18)));

        currentMode = new TextView(this);
        currentMode.setTextSize(15);
        currentMode.setTextColor(Color.rgb(26, 51, 51));
        currentMode.setTypeface(null, android.graphics.Typeface.BOLD);
        currentMode.setPadding(dp(16), dp(14), dp(16), dp(14));
        currentMode.setBackground(roundedBox(Color.WHITE, Color.rgb(196, 210, 208), 14));
        root.addView(currentMode, fullWrap(dp(22)));
        refreshCurrentMode();

        Button biometric = optionButton("指纹或面容", "调用 Android 系统生物识别，不保存生物特征数据");
        root.addView(biometric, fullWrap(dp(12)));
        biometric.setOnClickListener(v -> configureBiometric());

        Button pin = optionButton("四位数字密码", "设置四位数字，密码只以加盐哈希形式保存在本机");
        root.addView(pin, fullWrap(dp(12)));
        pin.setOnClickListener(v -> configurePin());

        Button pattern = optionButton("九宫格图形锁", "至少连接四个点，需要连续绘制两次确认");
        root.addView(pattern, fullWrap(dp(12)));
        pattern.setOnClickListener(v -> configurePattern());

        Button disable = optionButton("关闭安全验证", "进入应用时不再要求验证");
        root.addView(disable, fullWrap(dp(18)));
        disable.setOnClickListener(v -> confirmDisable());

        TextView note = new TextView(this);
        note.setText("提示：如果选择生物识别，请确保系统中已录入指纹或面容。删除系统中的全部生物识别后，可能无法通过该方式进入应用。");
        note.setTextSize(12.5f);
        note.setTextColor(Color.rgb(105, 120, 120));
        note.setLineSpacing(0, 1.12f);
        root.addView(note, fullWrap(0));

        setContentView(scrollView);
    }

    private Button optionButton(String title, String subtitle) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setText(title + "\n" + subtitle);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(25, 50, 50));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(roundedBox(Color.WHITE, Color.rgb(205, 218, 216), 14));
        button.setMinHeight(dp(72));
        return button;
    }

    private void configureBiometric() {
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK;
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            new AlertDialog.Builder(this)
                    .setTitle("无法启用生物识别")
                    .setMessage("请先在手机系统设置中录入指纹或面容，然后再回来启用。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        AppSecurity.setBiometric(SecuritySettingsActivity.this);
                        securityChanged("已启用指纹或面容验证");
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(SecuritySettingsActivity.this, "未识别，请重试", Toast.LENGTH_SHORT).show();
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("启用生物识别")
                .setSubtitle("验证一次以确认该设备可用")
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("取消")
                .setConfirmationRequired(false)
                .build();
        prompt.authenticate(info);
    }

    private void configurePin() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText first = createPinInput("输入四位数字密码");
        EditText second = createPinInput("再次输入确认");
        form.addView(first, fullWrap(dp(10)));
        form.addView(second, fullWrap(0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设置四位数字密码")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String one = first.getText().toString();
            String two = second.getText().toString();
            if (one.length() != 4) { first.setError("请输入四位数字"); return; }
            if (!one.equals(two)) { second.setError("两次输入不一致"); return; }
            AppSecurity.setPin(this, one);
            dialog.dismiss();
            securityChanged("已启用四位数字密码");
        }));
        dialog.show();
    }

    private EditText createPinInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(20);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(roundedBox(Color.WHITE, Color.rgb(196, 210, 208), 12));
        return input;
    }

    private void configurePattern() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), 0);
        TextView instruction = new TextView(this);
        instruction.setText("绘制新图形，至少连接四个点");
        instruction.setTextSize(15);
        instruction.setTextColor(Color.rgb(50, 70, 70));
        instruction.setGravity(Gravity.CENTER);
        content.addView(instruction, fullWrap(dp(8)));
        PatternLockView patternView = new PatternLockView(this);
        content.addView(patternView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        final String[] firstPattern = {null};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设置九宫格图形锁")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        patternView.setListener(pattern -> {
            int pointCount = pattern.isEmpty() ? 0 : pattern.split("-").length;
            if (pointCount < 4) {
                instruction.setText("至少需要连接四个点，请重新绘制");
                patternView.postDelayed(patternView::clearPattern, 450);
                return;
            }
            if (firstPattern[0] == null) {
                firstPattern[0] = pattern;
                instruction.setText("请再次绘制相同图形进行确认");
                patternView.postDelayed(patternView::clearPattern, 350);
                return;
            }
            if (!firstPattern[0].equals(pattern)) {
                firstPattern[0] = null;
                instruction.setText("两次图形不一致，请重新设置");
                patternView.postDelayed(patternView::clearPattern, 450);
                return;
            }
            AppSecurity.setPattern(this, pattern);
            dialog.dismiss();
            securityChanged("已启用九宫格图形锁");
        });
        dialog.show();
    }

    private void confirmDisable() {
        if (!AppSecurity.isEnabled(this)) {
            Toast.makeText(this, "安全验证目前未启用", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("关闭安全验证")
                .setMessage("关闭后，打开 EZ记账 将不再要求指纹、密码或图形验证。")
                .setNegativeButton("取消", null)
                .setPositiveButton("关闭", (dialog, which) -> {
                    AppSecurity.disable(this);
                    securityChanged("已关闭安全验证");
                })
                .show();
    }

    private void securityChanged(String text) {
        setResult(Activity.RESULT_OK);
        refreshCurrentMode();
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void refreshCurrentMode() {
        currentMode.setText("当前方式：" + AppSecurity.getModeLabel(this));
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

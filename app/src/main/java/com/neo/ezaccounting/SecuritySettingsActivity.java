package com.neo.ezaccounting;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecuritySettingsActivity extends FragmentActivity {
    private final ExecutorService securityExecutor = Executors.newSingleThreadExecutor();
    private TextView currentMode;
    private TextView relockButton;
    private TextView screenOffButton;
    private TextView preloadButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiTheme.applySystemBars(this);
        showSettings();
    }

    private void showSettings() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(UiTheme.background(this));
        scrollView.setPadding(0, 0, 0, dp(24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(UiComponents.PAGE_HORIZONTAL_DP), dp(UiComponents.PAGE_TOP_DP),
                dp(UiComponents.PAGE_HORIZONTAL_DP), dp(UiComponents.PAGE_BOTTOM_DP));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("安全与隐私");
        title.setTextSize(28);
        title.setTextColor(UiTheme.primaryText(this));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(28);
        close.setTextColor(UiTheme.secondaryText(this));
        UiComponents.styleIconButton(close);
        close.setContentDescription("返回设置中心");
        close.setOnClickListener(view -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, fullWrap(dp(10)));

        TextView description = new TextView(this);
        description.setText("管理进入拾账时的身份验证、自动锁定与安全预加载策略。");
        description.setTextSize(14.5f);
        description.setTextColor(UiTheme.secondaryText(this));
        description.setLineSpacing(0, 1.18f);
        root.addView(description, fullWrap(dp(24)));

        currentMode = new TextView(this);
        currentMode.setTextSize(15);
        currentMode.setTextColor(UiTheme.primaryText(this));
        currentMode.setLineSpacing(0, 1.18f);
        currentMode.setPadding(dp(16), dp(14), dp(16), dp(14));
        currentMode.setBackground(outlinedSurface());
        currentMode.setContentDescription("当前安全状态");
        root.addView(currentMode, fullWrap(dp(24)));

        TextView policyTitle = sectionTitle("锁定策略");
        root.addView(policyTitle, fullWrap(dp(8)));

        LinearLayout policyCard = settingsCard();
        relockButton = optionRow("自动锁定时间", "设置 App 切到后台后多长时间重新验证");
        policyCard.addView(relockButton, rowParams());
        relockButton.setOnClickListener(v -> chooseRelockTimeout());
        addDivider(policyCard);
        screenOffButton = optionRow("熄屏后立即锁定", "手机变为非交互状态后，下次进入立即验证");
        policyCard.addView(screenOffButton, rowParams());
        screenOffButton.setOnClickListener(v -> toggleScreenOffLock());
        addDivider(policyCard);
        preloadButton = optionRow("解锁时预加载首页", "验证期间安全加载首页，加快解锁后的显示");
        policyCard.addView(preloadButton, rowParams());
        preloadButton.setOnClickListener(v -> toggleUnlockPreload());
        root.addView(policyCard, fullWrap(dp(24)));

        TextView methodTitle = sectionTitle("验证方式");
        root.addView(methodTitle, fullWrap(dp(8)));

        LinearLayout methodCard = settingsCard();
        TextView biometric = optionRow("指纹或面容", "调用 Android 系统生物识别，不保存生物特征数据");
        methodCard.addView(biometric, rowParams());
        biometric.setOnClickListener(v -> configureBiometric());
        addDivider(methodCard);
        TextView pin = optionRow("四位数字密码", "密码仅以加盐哈希形式保存在本机");
        methodCard.addView(pin, rowParams());
        pin.setOnClickListener(v -> configurePin());
        addDivider(methodCard);
        TextView pattern = optionRow("九宫格图形锁", "至少连接四个点，需要连续绘制两次确认");
        methodCard.addView(pattern, rowParams());
        pattern.setOnClickListener(v -> configurePattern());
        root.addView(methodCard, fullWrap(dp(18)));

        Button disable = new Button(this);
        disable.setText("关闭安全验证");
        UiComponents.styleDangerAction(disable);
        root.addView(disable, fullWrap(dp(18)));
        disable.setOnClickListener(v -> confirmDisable());

        TextView note = new TextView(this);
        note.setText("提示：连续输错 PIN 或图形后会进入递增等待时间。若选择生物识别，请确保系统中已录入指纹或面容。");
        note.setTextSize(12.5f);
        note.setTextColor(UiTheme.tertiaryText(this));
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.15f);
        root.addView(note, fullWrap(0));

        refreshSummary();
        setContentView(scrollView);
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(14.5f);
        title.setTextColor(UiTheme.primaryText(this));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        return title;
    }

    private LinearLayout settingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(outlinedSurface());
        card.setClipToOutline(true);
        return card;
    }

    private TextView optionRow(String title, String subtitle) {
        TextView row = new TextView(this);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setText(title + "\n" + subtitle);
        row.setTextSize(15);
        row.setTextColor(UiTheme.primaryText(this));
        row.setLineSpacing(0, 1.12f);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setMinHeight(dp(68));
        row.setClickable(true);
        row.setFocusable(true);
        GradientDrawable transparent = new GradientDrawable();
        transparent.setColor(Color.TRANSPARENT);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(
                UiTheme.isDark(this) ? 64 : 40, 23, 107, 91)), transparent, null));
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addDivider(LinearLayout card) {
        View divider = new View(this);
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        divider.setBackgroundColor(UiTheme.border(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        card.addView(divider, params);
    }

    private GradientDrawable outlinedSurface() {
        GradientDrawable drawable = UiComponents.surface(this);
        drawable.setStroke(dp(1), UiTheme.border(this));
        return drawable;
    }

    private void chooseRelockTimeout() {
        String[] labels = {"立即", "15秒", "1分钟", "5分钟", "仅完全退出后"};
        long[] values = {
                AppSecurity.RELOCK_IMMEDIATELY,
                AppSecurity.RELOCK_AFTER_15_SECONDS,
                AppSecurity.RELOCK_AFTER_1_MINUTE,
                AppSecurity.RELOCK_AFTER_5_MINUTES,
                AppSecurity.RELOCK_ON_COLD_START_ONLY
        };
        long current = AppSecurity.getRelockTimeoutMs(this);
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;

        UiComponents.show(new AlertDialog.Builder(this)
                .setTitle("自动锁定时间")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    AppSecurity.setRelockTimeoutMs(this, values[which]);
                    dialog.dismiss();
                    policyChanged("自动锁定时间已设为" + labels[which]);
                })
                .setNegativeButton("取消", null)
                .create());
    }

    private void toggleScreenOffLock() {
        boolean next = !AppSecurity.isLockOnScreenOff(this);
        AppSecurity.setLockOnScreenOff(this, next);
        policyChanged(next ? "已启用熄屏后立即锁定" : "已关闭熄屏后立即锁定");
    }

    private void configureBiometric() {
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK;
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            UiComponents.show(new AlertDialog.Builder(this)
                    .setTitle("无法启用生物识别")
                    .setMessage("请先在手机系统设置中录入指纹或面容，然后再回来启用。")
                    .setPositiveButton("知道了", null)
                    .create());
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
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(false);
            save.setText("保存中…");
            first.setEnabled(false);
            second.setEnabled(false);
            securityExecutor.execute(() -> {
                try {
                    AppSecurity.setPin(getApplicationContext(), one);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        dialog.dismiss();
                        securityChanged("已启用四位数字密码");
                    });
                } catch (RuntimeException error) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        save.setEnabled(true);
                        save.setText("保存");
                        first.setEnabled(true);
                        second.setEnabled(true);
                        Toast.makeText(this, "保存密码失败，请重试", Toast.LENGTH_LONG).show();
                    });
                }
            });
        }));
        dialog.show();
        UiComponents.styleDialog(dialog);
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
        input.setBackground(roundedBox(UiTheme.surface(this), UiTheme.border(this), 12));
        return input;
    }

    private void configurePattern() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), 0);
        TextView instruction = new TextView(this);
        instruction.setText("绘制新图形，至少连接四个点");
        instruction.setTextSize(15);
        instruction.setTextColor(UiTheme.secondaryText(this));
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
            patternView.setEnabled(false);
            instruction.setText("正在安全保存图形…");
            securityExecutor.execute(() -> {
                try {
                    AppSecurity.setPattern(getApplicationContext(), pattern);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        dialog.dismiss();
                        securityChanged("已启用九宫格图形锁");
                    });
                } catch (RuntimeException error) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        firstPattern[0] = null;
                        patternView.setEnabled(true);
                        patternView.clearPattern();
                        instruction.setText("保存失败，请重新绘制");
                    });
                }
            });
        });
        dialog.show();
        UiComponents.styleDialog(dialog);
    }

    private void confirmDisable() {
        if (!AppSecurity.isEnabled(this)) {
            Toast.makeText(this, "安全验证目前未启用", Toast.LENGTH_SHORT).show();
            return;
        }
        UiComponents.show(new AlertDialog.Builder(this)
                .setTitle("关闭安全验证")
                .setMessage("关闭后，打开拾账将不再要求指纹、密码或图形验证。自动锁定策略会保留，重新启用时继续使用。")
                .setNegativeButton("取消", null)
                .setPositiveButton("关闭", (dialog, which) -> {
                    AppSecurity.disable(this);
                    securityChanged("已关闭安全验证");
                })
                .create());
    }

    private void securityChanged(String text) {
        setResult(Activity.RESULT_OK);
        refreshSummary();
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void policyChanged(String text) {
        setResult(Activity.RESULT_OK);
        refreshSummary();
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void toggleUnlockPreload() {
        boolean enabled = !AppSecurity.isPreloadWhileLocked(this);
        AppSecurity.setPreloadWhileLocked(this, enabled);
        policyChanged(enabled ? "已开启解锁时预加载" : "已关闭解锁时预加载");
    }

    private void refreshSummary() {
        String summary = "当前方式：" + AppSecurity.getModeLabel(this) +
                "\n自动锁定：" + AppSecurity.getRelockTimeoutLabel(this) +
                "\n熄屏锁定：" + (AppSecurity.isLockOnScreenOff(this) ? "开启" : "关闭") +
                "\n解锁预加载：" + (AppSecurity.isPreloadWhileLocked(this) ? "开启" : "关闭");
        currentMode.setText(summary);
        currentMode.setContentDescription("当前安全状态。" + summary.replace('\n', '。'));
        if (relockButton != null) {
            relockButton.setText("自动锁定时间：" + AppSecurity.getRelockTimeoutLabel(this) +
                    "\n设置 App 切到后台后多长时间重新验证");
        }
        if (screenOffButton != null) {
            screenOffButton.setText("熄屏后立即锁定：" +
                    (AppSecurity.isLockOnScreenOff(this) ? "开启" : "关闭") +
                    "\n手机变为非交互状态后，下次进入立即验证");
        }
        if (preloadButton != null) {
            preloadButton.setText("解锁时预加载首页：" +
                    (AppSecurity.isPreloadWhileLocked(this) ? "开启" : "关闭") +
                    "\n验证期间安全加载首页，加快解锁后的显示");
        }
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

    @Override
    protected void onDestroy() {
        securityExecutor.shutdownNow();
        super.onDestroy();
    }
}

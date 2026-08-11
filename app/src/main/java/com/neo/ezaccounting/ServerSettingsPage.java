package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ServerSettingsPage {
    public interface AddressTestCallback {
        void onResult(boolean reachable, String detail);
    }

    public interface Listener {
        void onSaved(List<LocalRouteRule> localRules, String publicUrl);
        void onWifiPermissionRequested(Runnable refreshAfterPermission);
        void onTestAddress(String url, AddressTestCallback callback);
        void onClose();
    }

    private static final class ImeState {
        int bottomInset;
    }

    private ServerSettingsPage() {}

    public static View create(Activity activity, List<LocalRouteRule> localRules,
                              String publicUrl, String activeLocalUrl,
                              Listener listener) {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(UiTheme.background(activity));
        scrollView.setPadding(0, 0, 0, dp(activity, 24));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(activity, UiComponents.PAGE_HORIZONTAL_DP),
                dp(activity, UiComponents.PAGE_TOP_DP),
                dp(activity, UiComponents.PAGE_HORIZONTAL_DP),
                dp(activity, UiComponents.PAGE_BOTTOM_DP));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(activity, "连接与线路", 28,
                UiTheme.primaryText(activity), true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text(activity, "×", 28, UiTheme.secondaryText(activity), false);
        UiComponents.styleIconButton(close);
        close.setContentDescription("返回设置中心");
        close.setOnClickListener(view -> listener.onClose());
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        content.addView(header, fullWrap(activity, 10));

        TextView description = text(activity,
                "配置本地和公网服务器地址。自动模式会检测可用性并选择合适线路。",
                14.5f, UiTheme.secondaryText(activity), false);
        description.setLineSpacing(0, 1.18f);
        content.addView(description, fullWrap(activity, 24));

        ImeState imeState = new ImeState();
        installImeAwareScrolling(activity, scrollView, imeState);

        TextView localLabel = text(activity, "局域网地址", 14.5f,
                UiTheme.primaryText(activity), true);
        content.addView(localLabel, fullWrap(activity, 8));

        String currentSsid = WifiRouteContext.currentSsid(activity);
        TextView wifiStatus = text(activity,
                currentSsid == null ? "按 Wi-Fi 名称自动选择局域网地址" :
                        "当前 Wi-Fi：" + currentSsid,
                13, UiTheme.secondaryText(activity), false);
        content.addView(wifiStatus, fullWrap(activity, 10));

        if (!WifiRouteContext.canReadSsid(activity)) {
            Button permission = new Button(activity);
            permission.setText("允许识别当前 Wi-Fi");
            UiComponents.styleSecondary(permission);
            permission.setOnClickListener(view -> listener.onWifiPermissionRequested(() -> {
                String refreshedSsid = WifiRouteContext.currentSsid(activity);
                wifiStatus.setText(refreshedSsid == null ?
                        "暂时无法识别当前 Wi-Fi，可手动填写名称" :
                        "当前 Wi-Fi：" + refreshedSsid);
                if (WifiRouteContext.canReadSsid(activity)) {
                    permission.setVisibility(View.GONE);
                }
            }));
            content.addView(permission, fullWrap(activity, 12));
        }

        LinearLayout ruleList = new LinearLayout(activity);
        ruleList.setOrientation(LinearLayout.VERTICAL);
        content.addView(ruleList, fullWrap(activity, 10));
        List<RuleInputs> ruleInputs = new ArrayList<>();
        List<LocalRouteRule> initialRules = localRules == null ?
                new ArrayList<>() : new ArrayList<>(localRules);
        if (initialRules.isEmpty()) initialRules.add(new LocalRouteRule("", ""));
        LocalRouteRule activeRule = activeLocalUrl == null ? null :
                LocalRouteRules.selectRule(initialRules, currentSsid,
                        WifiRouteContext.isWifiConnected(activity));
        for (LocalRouteRule rule : initialRules) {
            boolean active = rule.equals(activeRule) && rule.url.equals(activeLocalUrl);
            addRuleRow(activity, ruleList, ruleInputs, rule, currentSsid, active,
                    scrollView, imeState, listener);
        }

        Button addRule = new Button(activity);
        addRule.setText("＋ 添加局域网地址");
        UiComponents.styleSecondary(addRule);
        addRule.setOnClickListener(view -> {
            addRuleRow(activity, ruleList, ruleInputs,
                    new LocalRouteRule("", currentSsid == null ? "" : currentSsid, ""),
                    currentSsid, false, scrollView, imeState, listener);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
        content.addView(addRule, fullWrap(activity, 18));

        TextView publicLabel = text(activity, "公网地址 · 未匹配或本地不可用时使用", 14.5f,
                UiTheme.primaryText(activity), true);
        EditText publicInput = input(activity, "https://money.example.com", publicUrl);
        publicInput.setId(View.generateViewId());
        publicLabel.setLabelFor(publicInput.getId());
        content.addView(publicLabel, fullWrap(activity, 8));
        publicInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        content.addView(publicInput, fullWrap(activity, 18));

        Button save = new Button(activity);
        save.setText("保存并连接");
        save.setTextSize(16);
        UiComponents.stylePrimary(save);
        content.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = text(activity,
                "可在快捷中心刷新页面；双指快速双击可打开隐藏菜单。\n" +
                        "HTTPS证书无效时会阻止连接。地址和安全设置只保存在本机。",
                12.5f, UiTheme.tertiaryText(activity), false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.15f);
        content.addView(note, fullWrap(activity, 22));

        keepVisibleAboveKeyboard(activity, scrollView, publicInput, imeState);

        publicInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            save.performClick();
            return true;
        });

        save.setOnClickListener(v -> {
            String publicRaw = publicInput.getText().toString();
            List<LocalRouteRule> normalizedRules = new ArrayList<>();
            Set<String> usedSsids = new HashSet<>();
            for (RuleInputs fields : new ArrayList<>(ruleInputs)) {
                String raw = fields.url.getText().toString();
                if (blank(raw)) continue;
                String ssidValue = fields.ssid.getText().toString().trim();
                String ssidKey = ssidValue.toLowerCase(Locale.ROOT);
                if (!usedSsids.add(ssidKey)) {
                    fields.ssid.setError(ssidKey.isEmpty() ?
                            "只能设置一个默认地址" : "该 Wi-Fi 已经配置过地址");
                    fields.ssid.requestFocus();
                    scrollFocusedFieldIntoView(activity, scrollView, fields.ssid, imeState);
                    return;
                }
                String normalized = ServerAddressValidator.normalize(raw);
                if (normalized == null) {
                    fields.url.setError("请输入有效的 HTTP 或 HTTPS 地址");
                    fields.url.requestFocus();
                    scrollFocusedFieldIntoView(activity, scrollView, fields.url, imeState);
                    return;
                }
                normalizedRules.add(new LocalRouteRule(
                        fields.name.getText().toString(), ssidValue, normalized));
            }
            if (normalizedRules.isEmpty() && blank(publicRaw)) {
                publicInput.setError("请至少填写一个局域网或公网地址");
                publicInput.requestFocus();
                scrollFocusedFieldIntoView(activity, scrollView, publicInput, imeState);
                return;
            }
            String normalizedPublic = ServerAddressValidator.normalize(publicRaw);
            if (!blank(publicRaw) && normalizedPublic == null) {
                publicInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                publicInput.requestFocus();
                scrollFocusedFieldIntoView(activity, scrollView, publicInput, imeState);
                return;
            }
            listener.onSaved(normalizedRules,
                    normalizedPublic == null ? "" : normalizedPublic);
        });
        return scrollView;
    }

    private static final class RuleInputs {
        final EditText name;
        final EditText ssid;
        final EditText url;

        RuleInputs(EditText name, EditText ssid, EditText url) {
            this.name = name;
            this.ssid = ssid;
            this.url = url;
        }
    }

    private static void addRuleRow(Activity activity, LinearLayout ruleList,
                                   List<RuleInputs> inputs, LocalRouteRule rule,
                                   String currentSsid, boolean active,
                                   ScrollView scrollView, ImeState imeState, Listener listener) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(activity));
        background.setStroke(dp(activity, 1), UiTheme.border(activity));
        background.setCornerRadius(dp(activity, 16));
        card.setBackground(background);

        TextView state = text(activity, active ? "使用中" :
                        (rule.isDefault() ? "默认地址" : "指定 Wi-Fi"),
                12.5f, active ? UiTheme.accent(activity) : UiTheme.secondaryText(activity),
                active);
        state.setContentDescription(active ? "当前正在使用此局域网地址" : null);
        card.addView(state, fullWrap(activity, 8));

        EditText name = input(activity, "规则名称，例如：家里", rule.name);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setContentDescription("规则名称");
        name.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(name, fullWrap(activity, 10));

        EditText ssid = input(activity,
                currentSsid == null ? "Wi-Fi 名称；留空作为默认地址" : currentSsid,
                rule.ssid);
        ssid.setInputType(InputType.TYPE_CLASS_TEXT);
        ssid.setContentDescription("Wi-Fi 名称");
        ssid.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (!active) {
            ssid.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start,
                                                        int count, int after) {}
                @Override public void onTextChanged(CharSequence value, int start,
                                                     int before, int count) {}
                @Override public void afterTextChanged(Editable value) {
                    state.setText(value.toString().trim().isEmpty() ?
                            "默认地址" : "指定 Wi-Fi");
                }
            });
        }
        card.addView(ssid, fullWrap(activity, 10));

        EditText url = input(activity, "http://192.168.1.100:8080", rule.url);
        url.setContentDescription("局域网服务器地址");
        url.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(url, fullWrap(activity, 8));

        TextView testStatus = text(activity, "", 12.5f,
                UiTheme.secondaryText(activity), false);
        testStatus.setVisibility(View.GONE);
        card.addView(testStatus, fullWrap(activity, 6));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button useWifi = new Button(activity);
        useWifi.setText("当前 Wi-Fi");
        useWifi.setContentDescription("使用当前 Wi-Fi 名称");
        UiComponents.styleSecondary(useWifi);
        actions.addView(useWifi, weightedWrap(activity, 1f, 4));

        Button test = new Button(activity);
        test.setText("测试");
        test.setContentDescription("测试此局域网地址");
        UiComponents.styleSecondary(test);
        actions.addView(test, weightedWrap(activity, 1f, 4));

        Button remove = new Button(activity);
        remove.setText("删除");
        remove.setContentDescription("删除此局域网地址");
        UiComponents.styleSecondary(remove);
        actions.addView(remove, weightedWrap(activity, 1f, 0));
        card.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RuleInputs fields = new RuleInputs(name, ssid, url);
        inputs.add(fields);
        useWifi.setOnClickListener(view -> fillCurrentWifi(activity, ssid, listener));
        test.setOnClickListener(view -> {
            String normalized = ServerAddressValidator.normalize(url.getText().toString());
            if (normalized == null) {
                url.setError("请先输入有效的 HTTP 或 HTTPS 地址");
                url.requestFocus();
                return;
            }
            test.setEnabled(false);
            testStatus.setText("正在测试连接…");
            testStatus.setTextColor(UiTheme.secondaryText(activity));
            testStatus.setVisibility(View.VISIBLE);
            listener.onTestAddress(normalized, (reachable, detail) -> {
                if (!test.isAttachedToWindow()) return;
                test.setEnabled(true);
                testStatus.setText((reachable ? "连接正常 · " : "连接失败 · ") + detail);
                testStatus.setTextColor(reachable ?
                        UiTheme.accent(activity) :
                        UiTheme.danger(activity));
            });
        });
        remove.setOnClickListener(view -> {
            inputs.remove(fields);
            ruleList.removeView(card);
        });
        keepVisibleAboveKeyboard(activity, scrollView, name, imeState);
        keepVisibleAboveKeyboard(activity, scrollView, ssid, imeState);
        keepVisibleAboveKeyboard(activity, scrollView, url, imeState);
        ruleList.addView(card, fullWrap(activity, 10));
    }

    private static void fillCurrentWifi(Activity activity, EditText ssid, Listener listener) {
        String current = WifiRouteContext.currentSsid(activity);
        if (current != null) {
            ssid.setText(current);
            ssid.setSelection(current.length());
            return;
        }
        if (!WifiRouteContext.canReadSsid(activity)) {
            listener.onWifiPermissionRequested(() -> {
                String refreshed = WifiRouteContext.currentSsid(activity);
                if (refreshed != null && ssid.isAttachedToWindow()) {
                    ssid.setText(refreshed);
                    ssid.setSelection(refreshed.length());
                }
            });
            return;
        }
        android.widget.Toast.makeText(activity,
                "暂时无法读取 Wi-Fi 名称，请确认已连接 Wi-Fi 且系统定位已开启",
                android.widget.Toast.LENGTH_LONG).show();
    }

    private static void installImeAwareScrolling(Activity activity, ScrollView scrollView,
                                                 ImeState state) {
        final int baseBottomPadding = dp(activity, 24);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
                boolean imeVisible = insets.isVisible(WindowInsets.Type.ime());
                int imeBottom = imeVisible
                        ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
                int navigationBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                state.bottomInset = Math.max(0, imeBottom);

                int desiredBottomPadding = baseBottomPadding +
                        Math.max(state.bottomInset, navigationBottom);
                if (view.getPaddingBottom() != desiredBottomPadding) {
                    view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                            view.getPaddingRight(), desiredBottomPadding);
                }

                if (imeVisible) {
                    View focused = activity.getCurrentFocus();
                    if (focused != null) {
                        scrollFocusedFieldIntoView(activity, scrollView, focused, state);
                    }
                }
                return insets;
            });
            scrollView.post(scrollView::requestApplyInsets);
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        Rect visibleFrame = new Rect();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            decorView.getWindowVisibleDisplayFrame(visibleFrame);
            int rootHeight = decorView.getRootView().getHeight();
            int hiddenHeight = Math.max(0, rootHeight - visibleFrame.bottom);
            int keyboardHeight = hiddenHeight > rootHeight * 0.15f ? hiddenHeight : 0;
            state.bottomInset = keyboardHeight;

            int desiredBottomPadding = baseBottomPadding + keyboardHeight;
            if (scrollView.getPaddingBottom() != desiredBottomPadding) {
                scrollView.setPadding(scrollView.getPaddingLeft(), scrollView.getPaddingTop(),
                        scrollView.getPaddingRight(), desiredBottomPadding);
            }

            if (keyboardHeight > 0) {
                View focused = activity.getCurrentFocus();
                if (focused != null) {
                    scrollFocusedFieldIntoView(activity, scrollView, focused, state);
                }
            }
        };
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        scrollView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {}

            @Override
            public void onViewDetachedFromWindow(View view) {
                if (decorView.getViewTreeObserver().isAlive()) {
                    decorView.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                }
                scrollView.removeOnAttachStateChangeListener(this);
            }
        });
    }

    private static void keepVisibleAboveKeyboard(Activity activity, ScrollView scrollView,
                                                  EditText input, ImeState state) {
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) scrollFocusedFieldIntoView(activity, scrollView, view, state);
        });
    }

    private static void scrollFocusedFieldIntoView(Activity activity, ScrollView scrollView,
                                                    View target, ImeState state) {
        scrollView.postDelayed(() -> {
            if (!target.isAttachedToWindow()) return;

            int margin = dp(activity, 24);
            int[] targetLocation = new int[2];
            int[] scrollLocation = new int[2];
            int[] decorLocation = new int[2];
            target.getLocationOnScreen(targetLocation);
            scrollView.getLocationOnScreen(scrollLocation);
            View decorView = activity.getWindow().getDecorView();
            decorView.getLocationOnScreen(decorLocation);

            int targetTop = targetLocation[1];
            int targetBottom = targetTop + target.getHeight();
            int scrollTop = scrollLocation[1];
            int scrollBottom = scrollTop + scrollView.getHeight();
            int windowBottom = decorLocation[1] + decorView.getHeight();

            Rect visibleFrame = new Rect();
            decorView.getWindowVisibleDisplayFrame(visibleFrame);
            int frameObstruction = Math.max(0, windowBottom - visibleFrame.bottom);
            boolean frameShowsKeyboard = frameObstruction > decorView.getHeight() * 0.15f;

            int keyboardTop = Integer.MAX_VALUE;
            if (frameShowsKeyboard) keyboardTop = visibleFrame.bottom;
            if (state.bottomInset > 0) {
                keyboardTop = Math.min(keyboardTop, windowBottom - state.bottomInset);
            }

            int safeBottom = scrollBottom;
            if (keyboardTop != Integer.MAX_VALUE) safeBottom = Math.min(safeBottom, keyboardTop);
            safeBottom -= margin;
            int safeTop = Math.max(scrollTop + margin, visibleFrame.top + margin);

            if (targetBottom > safeBottom) {
                scrollView.smoothScrollBy(0, targetBottom - safeBottom);
            } else if (targetTop < safeTop) {
                scrollView.smoothScrollBy(0, targetTop - safeTop);
            }
        }, 120L);
    }

    private static EditText input(Activity activity, String hint, String value) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSelectAllOnFocus(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextSize(16);
        input.setTextColor(UiTheme.primaryText(activity));
        input.setHintTextColor(UiTheme.tertiaryText(activity));
        input.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 16), dp(activity, 13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(activity));
        background.setStroke(dp(activity, 1), UiTheme.border(activity));
        background.setCornerRadius(dp(activity, 14));
        input.setBackground(background);
        return input;
    }

    private static TextView text(Activity activity, String value, float size, int color,
                                 boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams fullWrap(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, bottomDp);
        return params;
    }

    private static LinearLayout.LayoutParams weightedWrap(Activity activity, float weight,
                                                           int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMarginEnd(dp(activity, endMarginDp));
        return params;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

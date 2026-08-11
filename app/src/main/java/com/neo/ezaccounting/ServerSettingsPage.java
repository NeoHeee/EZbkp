package com.neo.ezaccounting;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
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

public final class ServerSettingsPage {
    public interface Listener {
        void onSaved(String localUrl, String publicUrl);
        void onClose();
    }

    private static final class ImeState {
        int bottomInset;
    }

    private ServerSettingsPage() {}

    public static View create(Activity activity, String localUrl, String publicUrl,
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

        TextView localLabel = text(activity, "本地地址", 14.5f,
                UiTheme.primaryText(activity), true);
        EditText localInput = input(activity, "http://192.168.1.100:8080", localUrl);
        localInput.setId(View.generateViewId());
        localLabel.setLabelFor(localInput.getId());
        content.addView(localLabel, fullWrap(activity, 8));
        localInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        content.addView(localInput, fullWrap(activity, 16));

        TextView publicLabel = text(activity, "公网地址", 14.5f,
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

        ImeState imeState = new ImeState();
        installImeAwareScrolling(activity, scrollView, imeState);
        keepVisibleAboveKeyboard(activity, scrollView, localInput, imeState);
        keepVisibleAboveKeyboard(activity, scrollView, publicInput, imeState);

        localInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) return false;
            publicInput.requestFocus();
            scrollFocusedFieldIntoView(activity, scrollView, publicInput, imeState);
            return true;
        });

        publicInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            save.performClick();
            return true;
        });

        save.setOnClickListener(v -> {
            String localRaw = localInput.getText().toString();
            String publicRaw = publicInput.getText().toString();
            if (blank(localRaw) && blank(publicRaw)) {
                localInput.setError("请至少填写一个地址");
                localInput.requestFocus();
                scrollFocusedFieldIntoView(activity, scrollView, localInput, imeState);
                return;
            }
            String normalizedLocal = ServerAddressValidator.normalize(localRaw);
            String normalizedPublic = ServerAddressValidator.normalize(publicRaw);
            if (!blank(localRaw) && normalizedLocal == null) {
                localInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                localInput.requestFocus();
                scrollFocusedFieldIntoView(activity, scrollView, localInput, imeState);
                return;
            }
            if (!blank(publicRaw) && normalizedPublic == null) {
                publicInput.setError("请输入有效的 HTTP 或 HTTPS 地址");
                publicInput.requestFocus();
                scrollFocusedFieldIntoView(activity, scrollView, publicInput, imeState);
                return;
            }
            listener.onSaved(normalizedLocal == null ? "" : normalizedLocal,
                    normalizedPublic == null ? "" : normalizedPublic);
        });
        return scrollView;
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

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

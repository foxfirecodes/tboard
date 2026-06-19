package dev.foxfire.tboard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TBoardInputMethodService extends InputMethodService {
    private static final String CODE_SHIFT = "SHIFT";
    private static final String CODE_CTRL = "CTRL";
    private static final String CODE_ALT = "ALT";
    private static final String CODE_BACKSPACE = "BACKSPACE";
    private static final String CODE_ENTER = "ENTER";
    private static final String CODE_SPACE = "SPACE";
    private static final String CODE_TAB = "TAB";
    private static final String CODE_ESC = "ESC";
    private static final String CODE_UP = "UP";
    private static final String CODE_DOWN = "DOWN";
    private static final String CODE_LEFT = "LEFT";
    private static final String CODE_RIGHT = "RIGHT";
    private static final String CODE_VOICE = "VOICE";
    private static final String CODE_COMPOSE = "COMPOSE";
    private static final String CODE_SNIPPETS = "SNIPPETS";
    private static final String CODE_SYMBOLS = "SYMBOLS";
    private static final String CODE_ALPHA = "ALPHA";

    private static final long SHIFT_DOUBLE_TAP_MS = 450L;
    private static final long SECONDARY_HOLD_MS = 250L;
    private static final long DELETE_REPEAT_MS = 55L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable deleteRepeatRunnable;

    private boolean shift;
    private boolean capsLock;
    private boolean ctrlLatch;
    private boolean altLatch;
    private boolean symbolMode;
    private boolean composeMode;
    private boolean snippetsMode;
    private boolean voiceActive;
    private boolean voiceListening;
    private long lastShiftTapTime;
    private final StringBuilder composeBuffer = new StringBuilder();
    private int composeCursor;
    private LinearLayout root;
    private TextView composeText;
    private TextView shiftKey;
    private TextView ctrlKey;
    private TextView altKey;
    private TextView voiceKey;
    private SpeechRecognizer speechRecognizer;
    private String speechRecognizerProvider;
    private Runnable voiceStartupTimeoutRunnable;

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.keyboard_background);
        updateRootPadding();
        buildKeyboard();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        updateRootPadding();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        cancelDeleteRepeat();
        stopVoiceInput();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        stopVoiceInput();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
            speechRecognizerProvider = null;
        }
        super.onDestroy();
    }

    private void updateRootPadding() {
        if (root == null) return;
        root.setPadding(dp(4), dp(5), dp(4), dp(shouldReserveSystemImeControlSpace() ? 34 : 5));
    }

    private boolean shouldReserveSystemImeControlSpace() {
        try {
            return shouldOfferSwitchingToNextInputMethod();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void buildKeyboard() {
        root.removeAllViews();
        shiftKey = null;
        ctrlKey = null;
        altKey = null;
        voiceKey = null;
        composeText = null;

        if (snippetsMode) {
            addSnippetsPanel();
        }
        if (composeMode) {
            addComposePanel();
        }
        addDevRow();
        if (symbolMode) {
            addSymbolRows();
        } else {
            addAlphaRows();
        }
        updateModifierLabels();
    }

    private void addDevRow() {
        addRow(45,
                key("Esc", CODE_ESC, 1.0f, Style.DEV),
                key("Tab", CODE_TAB, 1.0f, Style.DEV),
                key("Ctrl", CODE_CTRL, 0.95f, Style.DEV),
                key("Alt", CODE_ALT, 0.85f, Style.DEV),
                key("Mic", CODE_VOICE, 0.95f, Style.DEV),
                key(composeMode ? "Draft•" : "Draft", CODE_COMPOSE, 1.15f, Style.DEV),
                key(snippetsMode ? "Snip•" : "Snip", CODE_SNIPPETS, 1.0f, Style.DEV),
                key("↑", CODE_UP, 0.7f, Style.DEV),
                key("←", CODE_LEFT, 0.7f, Style.DEV),
                key("↓", CODE_DOWN, 0.7f, Style.DEV),
                key("→", CODE_RIGHT, 0.7f, Style.DEV));
    }

    private void addSnippetsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setBackgroundColor(Color.rgb(199, 200, 202));

        addSnippetRow(panel,
                new Snippet("git st", "git status"),
                new Snippet("ls -la", "ls -la"),
                new Snippet("cd", "cd "),
                new Snippet("clear", "clear"));
        addSnippetRow(panel,
                new Snippet("tmux ls", "tmux ls"),
                new Snippet("attach", "tmux attach -t "),
                new Snippet("new", "tmux new -s "),
                new Snippet("exit", "exit"));

        root.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addSnippetRow(LinearLayout panel, Snippet... snippets) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Snippet snippet : snippets) {
            row.addView(composeButton(snippet.label, v -> insertSnippet(snippet.text)),
                    new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        panel.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addComposePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setBackgroundColor(Color.rgb(199, 200, 202));

        composeText = new TextView(this);
        composeText.setTextSize(17f);
        composeText.setTextColor(Color.BLACK);
        composeText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        composeText.setMinLines(2);
        composeText.setMaxLines(4);
        composeText.setPadding(dp(10), dp(8), dp(10), dp(8));
        composeText.setBackgroundResource(R.drawable.key_background);
        composeText.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                moveComposeCursorToTouch(event);
            }
            return true;
        });
        panel.addView(composeText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(86)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(4), 0, 0);
        actions.addView(composeButton("Cancel", v -> closeCompose(false)), new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(composeButton("Clear", v -> clearComposeDraft()), new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(composeButton("Insert", v -> submitComposeDraft()), new LinearLayout.LayoutParams(0, dp(42), 1.25f));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateComposeText();
    }

    private TextView composeButton(String label, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(14f);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.rgb(35, 36, 38));
        button.setBackgroundResource(R.drawable.dev_key_background);
        button.setClickable(true);
        button.setOnClickListener(listener);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private void addAlphaRows() {
        addRow(57,
                key("q", "q", "1"), key("w", "w", "2"), key("e", "e", "3"), key("r", "r", "4"), key("t", "t", "5"),
                key("y", "y", "6"), key("u", "u", "7"), key("i", "i", "8"), key("o", "o", "9"), key("p", "p", "0"));

        addRowWithInsets(57, 0.48f, 0.48f,
                key("a", "a", "@"), key("s", "s", "#"), key("d", "d", "$"), key("f", "f", "_"), key("g", "g", "&"),
                key("h", "h", "-"), key("j", "j", "+"), key("k", "k", "("), key("l", "l", ")"));

        addRow(57,
                key("⇧", CODE_SHIFT, 1.45f, Style.ACTION),
                key("z", "z", "*"), key("x", "x", "\""), key("c", "c", "'"), key("v", "v", ":"),
                key("b", "b", ";"), key("n", "n", "!"), key("m", "m", "?"),
                key("⌫", CODE_BACKSPACE, 1.45f, Style.ACTION));

        addRow(59,
                key("?123", CODE_SYMBOLS, 1.45f, Style.ACTION),
                key(",", ",", 1f, Style.ACTION),
                key("", CODE_SPACE, 5.35f, Style.SPACE),
                key(".", ".", 1f, Style.ACTION),
                key("↵", CODE_ENTER, 1.55f, Style.ACTION));
    }

    private void addSymbolRows() {
        addRow(57,
                key("1", "1"), key("2", "2"), key("3", "3"), key("4", "4"), key("5", "5"),
                key("6", "6"), key("7", "7"), key("8", "8"), key("9", "9"), key("0", "0"));

        addRowWithInsets(57, 0.48f, 0.48f,
                key("@", "@"), key("#", "#"), key("$", "$"), key("_", "_"), key("&", "&"),
                key("-", "-"), key("+", "+"), key("(", "("), key(")", ")"));

        addRow(57,
                key("=", "=", 1.45f, Style.ACTION),
                key("*", "*"), key("\"", "\""), key("'", "'"), key(":", ":"), key(";", ";"),
                key("!", "!"), key("?", "?"),
                key("⌫", CODE_BACKSPACE, 1.45f, Style.ACTION));

        addRow(59,
                key("ABC", CODE_ALPHA, 1.45f, Style.ACTION),
                key("/", "/", 1f, Style.ACTION),
                key("", CODE_SPACE, 5.35f, Style.SPACE),
                key("|", "|", 1f, Style.ACTION),
                key("↵", CODE_ENTER, 1.55f, Style.ACTION));
    }

    private void addRow(int heightDp, KeySpec... keys) {
        addRowWithInsets(heightDp, 0f, 0f, keys);
    }

    private void addRowWithInsets(int heightDp, float leftInsetWeight, float rightInsetWeight, KeySpec... keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, 0);

        if (leftInsetWeight > 0f) {
            row.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(heightDp), leftInsetWeight));
        }

        for (KeySpec spec : keys) {
            row.addView(makeKeyCell(spec), new LinearLayout.LayoutParams(0, dp(heightDp), spec.weight));
        }

        if (rightInsetWeight > 0f) {
            row.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(heightDp), rightInsetWeight));
        }

        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /**
     * The FrameLayout is the touch target and fills the whole grid cell. The visible key is inset inside it,
     * so the visual gaps are still visible but taps in those gaps go to the nearest key.
     */
    private View makeKeyCell(KeySpec spec) {
        FrameLayout cell = new FrameLayout(this);
        cell.setClickable(spec.style != Style.INVISIBLE);

        if (TextUtils.equals(spec.code, CODE_BACKSPACE)) {
            cell.setOnTouchListener((v, event) -> handleBackspaceTouch(v, event));
        } else if (spec.style != Style.INVISIBLE) {
            if (spec.secondary != null) {
                final boolean[] secondaryTriggered = {false};
                final Runnable secondaryRunnable = () -> {
                    secondaryTriggered[0] = true;
                    commitSecondary(spec.secondary);
                };
                cell.setOnTouchListener((v, event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            secondaryTriggered[0] = false;
                            v.setPressed(true);
                            handler.postDelayed(secondaryRunnable, SECONDARY_HOLD_MS);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (event.getX() < 0 || event.getX() > v.getWidth()
                                    || event.getY() < 0 || event.getY() > v.getHeight()) {
                                handler.removeCallbacks(secondaryRunnable);
                                v.setPressed(false);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            handler.removeCallbacks(secondaryRunnable);
                            v.setPressed(false);
                            if (!secondaryTriggered[0]) {
                                handleKey(spec.code);
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            handler.removeCallbacks(secondaryRunnable);
                            v.setPressed(false);
                            return true;
                        default:
                            return true;
                    }
                });
            } else {
                cell.setOnClickListener(v -> handleKey(spec.code));
            }
        }

        View visual = makeKeyVisual(spec);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        int horizontalInset = dp(horizontalInsetFor(spec.style));
        int verticalInset = dp(verticalInsetFor(spec.style));
        lp.setMargins(horizontalInset, verticalInset, horizontalInset, verticalInset);
        cell.addView(visual, lp);
        return cell;
    }

    private View makeKeyVisual(KeySpec spec) {
        FrameLayout visual = new FrameLayout(this);
        visual.setBackgroundResource(backgroundFor(spec.style));
        visual.setDuplicateParentStateEnabled(true);

        TextView primary = new TextView(this);
        primary.setText(spec.display);
        primary.setTextSize(textSizeFor(spec));
        primary.setGravity(Gravity.CENTER);
        primary.setIncludeFontPadding(false);
        primary.setTextColor(textColorFor(spec.style));
        primary.setDuplicateParentStateEnabled(true);
        visual.addView(primary, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        if (spec.secondary != null) {
            TextView secondary = new TextView(this);
            secondary.setText(spec.secondary);
            secondary.setTextSize(9f);
            secondary.setGravity(Gravity.CENTER);
            secondary.setIncludeFontPadding(false);
            secondary.setTextColor(Color.rgb(87, 88, 90));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.RIGHT);
            lp.setMargins(0, 0, dp(4), dp(4));
            visual.addView(secondary, lp);
        }

        if (TextUtils.equals(spec.code, CODE_SHIFT)) shiftKey = primary;
        if (TextUtils.equals(spec.code, CODE_CTRL)) ctrlKey = primary;
        if (TextUtils.equals(spec.code, CODE_ALT)) altKey = primary;
        if (TextUtils.equals(spec.code, CODE_VOICE)) voiceKey = primary;
        return visual;
    }

    private boolean handleBackspaceTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                view.setPressed(true);
                sendBackspaceOnce();
                startDeleteRepeat();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.setPressed(false);
                cancelDeleteRepeat();
                return true;
            default:
                return true;
        }
    }

    private void sendBackspaceOnce() {
        if (composeMode) {
            deleteComposeBeforeCursor();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            sendKey(ic, KeyEvent.KEYCODE_DEL);
        }
    }

    private void startDeleteRepeat() {
        cancelDeleteRepeat();
        deleteRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (composeMode) {
                    deleteComposeBeforeCursor();
                    handler.postDelayed(this, DELETE_REPEAT_MS);
                    return;
                }
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    sendModifiedKey(ic, KeyEvent.KEYCODE_DEL, 0);
                    handler.postDelayed(this, DELETE_REPEAT_MS);
                }
            }
        };
        handler.postDelayed(deleteRepeatRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelDeleteRepeat() {
        if (deleteRepeatRunnable != null) {
            handler.removeCallbacks(deleteRepeatRunnable);
            deleteRepeatRunnable = null;
        }
    }

    private int horizontalInsetFor(Style style) {
        switch (style) {
            case DEV:
                return 2;
            case NAV:
            case INVISIBLE:
                return 6;
            default:
                return 2;
        }
    }

    private int verticalInsetFor(Style style) {
        switch (style) {
            case NAV:
            case INVISIBLE:
                return 5;
            default:
                return 3;
        }
    }

    private int backgroundFor(Style style) {
        switch (style) {
            case ACTION:
                return R.drawable.action_key_background;
            case DEV:
                return R.drawable.dev_key_background;
            case INVISIBLE:
            case NAV:
                return android.R.color.transparent;
            case SPACE:
            case NORMAL:
            default:
                return R.drawable.key_background;
        }
    }

    private int textColorFor(Style style) {
        switch (style) {
            case ACTION:
                return Color.WHITE;
            case DEV:
            case NAV:
                return Color.rgb(55, 56, 58);
            case INVISIBLE:
                return Color.TRANSPARENT;
            case SPACE:
            case NORMAL:
            default:
                return Color.BLACK;
        }
    }

    private float textSizeFor(KeySpec spec) {
        if (spec.style == Style.DEV) return 13f;
        if (spec.style == Style.NAV) return 23f;
        if (TextUtils.equals(spec.code, CODE_SPACE)) return 12f;
        if (TextUtils.equals(spec.code, CODE_SHIFT) || TextUtils.equals(spec.code, CODE_BACKSPACE) || TextUtils.equals(spec.code, CODE_ENTER)) return 24f;
        if (spec.display.length() > 2) return 14f;
        return 28f;
    }

    private KeySpec key(String display, String code) {
        return key(display, code, 1f, Style.NORMAL, null);
    }

    private KeySpec key(String display, String code, String secondary) {
        return key(display, code, 1f, Style.NORMAL, secondary);
    }

    private KeySpec key(String display, String code, float weight, Style style) {
        return key(display, code, weight, style, null);
    }

    private KeySpec key(String display, String code, float weight, Style style, String secondary) {
        return new KeySpec(display, code, weight, style, secondary);
    }

    private void handleKey(String code) {
        InputConnection ic = getCurrentInputConnection();

        switch (code) {
            case CODE_SHIFT:
                handleShiftTap();
                return;
            case CODE_CTRL:
                ctrlLatch = !ctrlLatch;
                updateModifierLabels();
                return;
            case CODE_ALT:
                altLatch = !altLatch;
                updateModifierLabels();
                return;
            case CODE_VOICE:
                toggleVoiceInput();
                return;
            case CODE_COMPOSE:
                openCompose();
                return;
            case CODE_SNIPPETS:
                toggleSnippets();
                return;
            case CODE_SYMBOLS:
                symbolMode = true;
                buildKeyboard();
                return;
            case CODE_ALPHA:
                symbolMode = false;
                buildKeyboard();
                return;
            default:
                break;
        }

        if (composeMode && handleComposeKey(code)) {
            return;
        }

        if (ic == null) return;

        switch (code) {
            case CODE_BACKSPACE:
                sendKey(ic, KeyEvent.KEYCODE_DEL);
                return;
            case CODE_ENTER:
                sendKey(ic, KeyEvent.KEYCODE_ENTER);
                return;
            case CODE_TAB:
                sendKey(ic, KeyEvent.KEYCODE_TAB);
                return;
            case CODE_ESC:
                sendKey(ic, KeyEvent.KEYCODE_ESCAPE);
                return;
            case CODE_UP:
                sendKey(ic, KeyEvent.KEYCODE_DPAD_UP);
                return;
            case CODE_DOWN:
                sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN);
                return;
            case CODE_LEFT:
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT);
                return;
            case CODE_RIGHT:
                sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT);
                return;
            case CODE_SPACE:
                commitText(ic, " ");
                return;
            default:
                commitText(ic, printable(code));
        }
    }

    private void toggleSnippets() {
        snippetsMode = !snippetsMode;
        buildKeyboard();
    }

    private void insertSnippet(String text) {
        if (composeMode) {
            insertComposeText(text);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private void openCompose() {
        if (composeMode) {
            closeCompose(true);
            return;
        }
        composeMode = true;
        buildKeyboard();
    }

    private void closeCompose(boolean keepDraft) {
        composeMode = false;
        if (!keepDraft) {
            composeBuffer.setLength(0);
            composeCursor = 0;
        }
        buildKeyboard();
    }

    private void submitComposeDraft() {
        if (composeBuffer.length() > 0) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(composeBuffer.toString(), 1);
            }
        }
        closeCompose(false);
    }

    private void clearComposeDraft() {
        composeBuffer.setLength(0);
        composeCursor = 0;
        updateComposeText();
    }

    private boolean handleComposeKey(String code) {
        switch (code) {
            case CODE_BACKSPACE:
                deleteComposeBeforeCursor();
                return true;
            case CODE_ENTER:
                insertComposeText("\n");
                return true;
            case CODE_TAB:
                insertComposeText("\t");
                return true;
            case CODE_SPACE:
                insertComposeText(" ");
                return true;
            case CODE_LEFT:
                if (composeCursor > 0) composeCursor--;
                updateComposeText();
                return true;
            case CODE_RIGHT:
                if (composeCursor < composeBuffer.length()) composeCursor++;
                updateComposeText();
                return true;
            case CODE_UP:
                composeCursor = 0;
                updateComposeText();
                return true;
            case CODE_DOWN:
                composeCursor = composeBuffer.length();
                updateComposeText();
                return true;
            case CODE_ESC:
                closeCompose(true);
                return true;
            default:
                insertComposeText(printable(code));
                return true;
        }
    }

    private void insertComposeText(String text) {
        if (TextUtils.isEmpty(text)) return;
        composeBuffer.insert(composeCursor, text);
        composeCursor += text.length();
        clearOneShotModifiers();
        updateComposeText();
    }

    private void deleteComposeBeforeCursor() {
        if (composeCursor <= 0 || composeBuffer.length() == 0) return;
        composeBuffer.deleteCharAt(composeCursor - 1);
        composeCursor--;
        updateComposeText();
    }

    private void updateComposeText() {
        if (composeText == null) return;
        composeCursor = clamp(composeCursor, 0, composeBuffer.length());

        String visibleText = composeBuffer.length() == 0
                ? "Draft text — tap Insert to send"
                : composeBuffer.toString();
        int visibleCursor = composeBuffer.length() == 0 ? 0 : composeCursor;

        SpannableStringBuilder display = new SpannableStringBuilder(visibleText);
        display.insert(visibleCursor, "\u200B");
        display.setSpan(new CursorSpan(), visibleCursor, visibleCursor + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        composeText.setText(display);
    }

    private void moveComposeCursorToTouch(MotionEvent event) {
        if (composeText == null || composeBuffer.length() == 0) return;
        Layout layout = composeText.getLayout();
        if (layout == null) return;

        int x = (int) event.getX() - composeText.getTotalPaddingLeft() + composeText.getScrollX();
        int y = (int) event.getY() - composeText.getTotalPaddingTop() + composeText.getScrollY();
        int line = clamp(layout.getLineForVertical(y), 0, layout.getLineCount() - 1);
        int offset = layout.getOffsetForHorizontal(line, x);
        if (offset > composeCursor) {
            offset--;
        }
        composeCursor = clamp(offset, 0, composeBuffer.length());
        updateComposeText();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void handleShiftTap() {
        long now = System.currentTimeMillis();
        if (capsLock) {
            capsLock = false;
            shift = false;
            lastShiftTapTime = 0L;
        } else if (lastShiftTapTime != 0L && now - lastShiftTapTime <= SHIFT_DOUBLE_TAP_MS) {
            capsLock = true;
            shift = true;
            lastShiftTapTime = 0L;
        } else {
            shift = !shift;
            lastShiftTapTime = now;
        }
        updateModifierLabels();
    }

    private String printable(String code) {
        if (code.length() == 1 && Character.isLetter(code.charAt(0))) {
            return (shift || capsLock) ? code.toUpperCase(Locale.US) : code;
        }
        return code;
    }

    private void commitSecondary(String secondary) {
        if (composeMode) {
            insertComposeText(secondary);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            commitText(ic, secondary);
        }
    }

    private void commitText(InputConnection ic, String text) {
        if (composeMode) {
            insertComposeText(text);
            return;
        }
        if (text.length() == 1) {
            int keyCode = keyCodeForChar(text.charAt(0));
            int meta = 0;
            if (ctrlLatch) meta |= KeyEvent.META_CTRL_ON;
            if (altLatch) meta |= KeyEvent.META_ALT_ON;
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN && meta != 0) {
                sendModifiedKey(ic, keyCode, meta);
                clearOneShotModifiers();
                return;
            }
        }

        ic.commitText(text, 1);
        clearOneShotModifiers();
    }

    private void sendKey(InputConnection ic, int keyCode) {
        int meta = 0;
        if (ctrlLatch) meta |= KeyEvent.META_CTRL_ON;
        if (altLatch) meta |= KeyEvent.META_ALT_ON;
        if (shift || capsLock) meta |= KeyEvent.META_SHIFT_ON;
        sendModifiedKey(ic, keyCode, meta);
        clearOneShotModifiers();
    }

    private void sendModifiedKey(InputConnection ic, int keyCode, int metaState) {
        long now = System.currentTimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState));
    }

    private int keyCodeForChar(char raw) {
        char c = Character.toLowerCase(raw);
        if (c >= 'a' && c <= 'z') return KeyEvent.KEYCODE_A + (c - 'a');
        if (c >= '1' && c <= '9') return KeyEvent.KEYCODE_1 + (c - '1');
        if (c == '0') return KeyEvent.KEYCODE_0;
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    private void clearOneShotModifiers() {
        ctrlLatch = false;
        altLatch = false;
        if (!capsLock) {
            shift = false;
        }
        updateModifierLabels();
    }

    private void updateModifierLabels() {
        if (shiftKey != null) {
            if (capsLock) {
                shiftKey.setText("⇪");
            } else {
                shiftKey.setText(shift ? "⇧•" : "⇧");
            }
        }
        if (ctrlKey != null) ctrlKey.setText(ctrlLatch ? "Ctrl•" : "Ctrl");
        if (altKey != null) altKey.setText(altLatch ? "Alt•" : "Alt");
        if (voiceKey != null) {
            if (voiceListening) {
                voiceKey.setText("Mic•");
            } else if (voiceActive) {
                voiceKey.setText("Mic…");
            } else {
                voiceKey.setText("Mic");
            }
        }
    }

    private void toggleVoiceInput() {
        if (voiceListening) {
            finishVoiceInput();
        } else if (voiceActive) {
            Toast.makeText(this, "Voice input is starting…", Toast.LENGTH_SHORT).show();
        } else {
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant microphone permission for TBoard voice input", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, SetupActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("requestMicPermission", true);
            startActivity(intent);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognizer available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ensureSpeechRecognizer()) {
            return;
        }

        voiceActive = true;
        voiceListening = false;
        updateModifierLabels();
        scheduleVoiceStartupTimeout();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
        intent.putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        speechRecognizer.startListening(intent);
    }

    private boolean ensureSpeechRecognizer() {
        String selectedProvider = selectedSpeechProvider();
        if (speechRecognizer != null && TextUtils.equals(selectedProvider, speechRecognizerProvider)) {
            return true;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
            speechRecognizerProvider = null;
        }

        try {
            ComponentName component = null;
            boolean useOnDevice = SetupActivity.SPEECH_PROVIDER_ON_DEVICE.equals(selectedProvider);
            if (!TextUtils.isEmpty(selectedProvider) && !useOnDevice) {
                component = ComponentName.unflattenFromString(selectedProvider);
                if (component == null || !isSpeechProviderAvailable(component)) {
                    Toast.makeText(this, "Selected voice provider unavailable; using system default", Toast.LENGTH_SHORT).show();
                    selectedProvider = SetupActivity.SPEECH_PROVIDER_SYSTEM_DEFAULT;
                    component = null;
                }
            }

            if (useOnDevice) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                speechRecognizer = component == null
                        ? SpeechRecognizer.createSpeechRecognizer(this)
                        : SpeechRecognizer.createSpeechRecognizer(this, component);
            }
            speechRecognizer.setRecognitionListener(new TBoardRecognitionListener());
            speechRecognizerProvider = selectedProvider;
            return true;
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not start selected voice provider", Toast.LENGTH_SHORT).show();
            speechRecognizer = null;
            speechRecognizerProvider = null;
            return false;
        }
    }

    private String selectedSpeechProvider() {
        SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(SetupActivity.PREF_SPEECH_PROVIDER,
                SetupActivity.SPEECH_PROVIDER_SYSTEM_DEFAULT);
    }

    private boolean isSpeechProviderAvailable(ComponentName component) {
        Intent intent = new Intent(SetupActivity.RECOGNITION_SERVICE_INTERFACE);
        intent.setComponent(component);
        List<ResolveInfo> services = getPackageManager().queryIntentServices(intent, PackageManager.MATCH_ALL);
        return !services.isEmpty();
    }

    private void finishVoiceInput() {
        if (speechRecognizer != null && voiceActive) {
            speechRecognizer.stopListening();
        }
        voiceListening = false;
        updateModifierLabels();
    }

    private void stopVoiceInput() {
        cancelVoiceStartupTimeout();
        if (speechRecognizer != null && voiceActive) {
            speechRecognizer.cancel();
        }
        voiceActive = false;
        voiceListening = false;
        updateModifierLabels();
    }

    private void scheduleVoiceStartupTimeout() {
        cancelVoiceStartupTimeout();
        voiceStartupTimeoutRunnable = () -> {
            if (voiceActive && !voiceListening) {
                stopVoiceInput();
                Toast.makeText(this, "Voice input did not become ready", Toast.LENGTH_SHORT).show();
            }
        };
        handler.postDelayed(voiceStartupTimeoutRunnable, 5000L);
    }

    private void cancelVoiceStartupTimeout() {
        if (voiceStartupTimeoutRunnable != null) {
            handler.removeCallbacks(voiceStartupTimeoutRunnable);
            voiceStartupTimeoutRunnable = null;
        }
    }

    private String bestSpeechResult(ArrayList<String> matches) {
        String first = matches.get(0);
        for (String match : matches) {
            if (looksFormatted(match)) {
                return match;
            }
        }
        return first;
    }

    private boolean looksFormatted(String text) {
        if (TextUtils.isEmpty(text)) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == ',' || c == '?' || c == '!' || c == ':' || c == ';') {
                return true;
            }
        }
        return Character.isUpperCase(text.charAt(0));
    }

    private void commitRecognizedSpeech(String text) {
        if (TextUtils.isEmpty(text)) return;
        if (composeMode) {
            insertComposeText(text);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private class TBoardRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {
            cancelVoiceStartupTimeout();
            voiceActive = true;
            voiceListening = true;
            updateModifierLabels();
        }
        @Override public void onBeginningOfSpeech() {
            voiceActive = true;
            voiceListening = true;
            updateModifierLabels();
        }
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {
            voiceListening = false;
            updateModifierLabels();
        }
        @Override public void onError(int error) {
            cancelVoiceStartupTimeout();
            voiceActive = false;
            voiceListening = false;
            updateModifierLabels();
            if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                Toast.makeText(TBoardInputMethodService.this, "Voice input failed", Toast.LENGTH_SHORT).show();
            }
        }
        @Override public void onResults(Bundle results) {
            cancelVoiceStartupTimeout();
            voiceActive = false;
            voiceListening = false;
            updateModifierLabels();
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                commitRecognizedSpeech(bestSpeechResult(matches));
            }
        }
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class CursorSpan extends ReplacementSpan {
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return 0;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            int originalColor = paint.getColor();
            paint.setColor(Color.BLACK);
            canvas.drawRect(x, top + dp(4), x + dp(2), bottom - dp(4), paint);
            paint.setColor(originalColor);
        }
    }

    private enum Style {
        NORMAL,
        ACTION,
        DEV,
        SPACE,
        NAV,
        INVISIBLE
    }

    private static class Snippet {
        final String label;
        final String text;

        Snippet(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    private static class KeySpec {
        final String display;
        final String code;
        final float weight;
        final Style style;
        final String secondary;

        KeySpec(String display, String code, float weight, Style style, String secondary) {
            this.display = display;
            this.code = code;
            this.weight = weight;
            this.style = style;
            this.secondary = secondary;
        }
    }
}

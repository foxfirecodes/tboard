package dev.foxfire.tboard;

import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

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
    private static final String CODE_SYMBOLS = "SYMBOLS";
    private static final String CODE_ALPHA = "ALPHA";
    private static final String CODE_HIDE = "HIDE";
    private static final String CODE_NEXT = "NEXT";

    private boolean shift;
    private boolean ctrlLatch;
    private boolean altLatch;
    private boolean symbolMode;
    private LinearLayout root;
    private TextView shiftKey;
    private TextView ctrlKey;
    private TextView altKey;

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4), dp(5), dp(4), dp(4));
        root.setBackgroundResource(R.drawable.keyboard_background);
        buildKeyboard();
        return root;
    }

    private void buildKeyboard() {
        root.removeAllViews();
        shiftKey = null;
        ctrlKey = null;
        altKey = null;

        addDevRow();
        if (symbolMode) {
            addSymbolRows();
        } else {
            addAlphaRows();
        }
        addBottomNavRow();
        updateModifierLabels();
    }

    private void addDevRow() {
        addRow(45,
                key("Esc", CODE_ESC, 1.15f, Style.DEV),
                key("Tab", CODE_TAB, 1.15f, Style.DEV),
                key("Ctrl", CODE_CTRL, 1.2f, Style.DEV),
                key("Alt", CODE_ALT, 1.1f, Style.DEV),
                key("↑", CODE_UP, 1f, Style.DEV),
                key("←", CODE_LEFT, 1f, Style.DEV),
                key("↓", CODE_DOWN, 1f, Style.DEV),
                key("→", CODE_RIGHT, 1f, Style.DEV));
    }

    private void addAlphaRows() {
        addRow(57,
                key("q¹", "q"), key("w²", "w"), key("e³", "e"), key("r⁴", "r"), key("t⁵", "t"),
                key("y⁶", "y"), key("u⁷", "u"), key("i⁸", "i"), key("o⁹", "o"), key("p⁰", "p"));

        addRowWithInsets(57, 0.48f, 0.48f,
                key("a", "a"), key("s", "s"), key("d", "d"), key("f", "f"), key("g", "g"),
                key("h", "h"), key("j", "j"), key("k", "k"), key("l", "l"));

        addRow(57,
                key("⇧", CODE_SHIFT, 1.45f, Style.ACTION),
                key("z", "z"), key("x", "x"), key("c", "c"), key("v", "v"),
                key("b", "b"), key("n", "n"), key("m", "m"),
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

    private void addBottomNavRow() {
        addRow(34,
                key("⌄", CODE_HIDE, 1f, Style.NAV),
                key("", CODE_SPACE, 5f, Style.INVISIBLE),
                key("◎", CODE_NEXT, 1f, Style.NAV));
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
        cell.setOnClickListener(v -> handleKey(spec.code));

        TextView visual = makeKeyVisual(spec);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        int horizontalInset = dp(horizontalInsetFor(spec.style));
        int verticalInset = dp(verticalInsetFor(spec.style));
        lp.setMargins(horizontalInset, verticalInset, horizontalInset, verticalInset);
        cell.addView(visual, lp);
        return cell;
    }

    private TextView makeKeyVisual(KeySpec spec) {
        TextView key = new TextView(this);
        key.setText(spec.display);
        key.setTextSize(textSizeFor(spec));
        key.setGravity(Gravity.CENTER);
        key.setIncludeFontPadding(false);
        key.setTextColor(textColorFor(spec.style));
        key.setBackgroundResource(backgroundFor(spec.style));
        key.setDuplicateParentStateEnabled(true);

        if (TextUtils.equals(spec.code, CODE_SHIFT)) shiftKey = key;
        if (TextUtils.equals(spec.code, CODE_CTRL)) ctrlKey = key;
        if (TextUtils.equals(spec.code, CODE_ALT)) altKey = key;
        return key;
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
        return key(display, code, 1f, Style.NORMAL);
    }

    private KeySpec key(String display, String code, float weight, Style style) {
        return new KeySpec(display, code, weight, style);
    }

    private void handleKey(String code) {
        InputConnection ic = getCurrentInputConnection();

        switch (code) {
            case CODE_SHIFT:
                shift = !shift;
                updateModifierLabels();
                return;
            case CODE_CTRL:
                ctrlLatch = !ctrlLatch;
                updateModifierLabels();
                return;
            case CODE_ALT:
                altLatch = !altLatch;
                updateModifierLabels();
                return;
            case CODE_SYMBOLS:
                symbolMode = true;
                buildKeyboard();
                return;
            case CODE_ALPHA:
                symbolMode = false;
                buildKeyboard();
                return;
            case CODE_HIDE:
                requestHideSelf(0);
                return;
            case CODE_NEXT:
                switchToNextInputMethod();
                return;
            default:
                break;
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

    private String printable(String code) {
        if (code.length() == 1 && Character.isLetter(code.charAt(0))) {
            return shift ? code.toUpperCase(Locale.US) : code;
        }
        return code;
    }

    private void commitText(InputConnection ic, String text) {
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
        if (shift) meta |= KeyEvent.META_SHIFT_ON;
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
        shift = false;
        updateModifierLabels();
    }

    private void updateModifierLabels() {
        if (shiftKey != null) shiftKey.setText(shift ? "⇧•" : "⇧");
        if (ctrlKey != null) ctrlKey.setText(ctrlLatch ? "Ctrl•" : "Ctrl");
        if (altKey != null) altKey.setText(altLatch ? "Alt•" : "Alt");
    }

    private void switchToNextInputMethod() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getWindow() != null && getWindow().getWindow() != null) {
            // Deprecated on newer Android, but still works across the minSdk range for a simple starter IME.
            imm.switchToNextInputMethod(getWindow().getWindow().getAttributes().token, false);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum Style {
        NORMAL,
        ACTION,
        DEV,
        SPACE,
        NAV,
        INVISIBLE
    }

    private static class KeySpec {
        final String display;
        final String code;
        final float weight;
        final Style style;

        KeySpec(String display, String code, float weight, Style style) {
            this.display = display;
            this.code = code;
            this.weight = weight;
            this.style = style;
        }
    }
}

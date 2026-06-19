package dev.foxfire.tboard;

import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class TBoardInputMethodService extends InputMethodService {
    private boolean shift;
    private boolean ctrlLatch;
    private boolean altLatch;
    private Button shiftKey;
    private Button ctrlKey;
    private Button altKey;

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(6), dp(6), dp(6), dp(8));
        root.setBackgroundResource(R.drawable.keyboard_background);

        addRow(root, "ESC", "TAB", "CTRL", "ALT", "UP", "BKSP");
        addRow(root, "q", "w", "e", "r", "t", "y", "u", "i", "o", "p");
        addRow(root, "a", "s", "d", "f", "g", "h", "j", "k", "l", "ENTER");
        addRow(root, "SHIFT", "z", "x", "c", "v", "b", "n", "m", "LEFT", "RIGHT");
        addRow(root, "123", "/", "-", "_", "|", ":", ";", "SPACE", "DOWN", "NEXT");

        updateModifierLabels();
        return root;
    }

    private void addRow(LinearLayout root, String... labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(2), 0, dp(2));

        for (String label : labels) {
            Button key = makeKey(label);
            row.addView(key, new LinearLayout.LayoutParams(0, dp(46), weightFor(label)));
        }
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private Button makeKey(String label) {
        Button key = new Button(this);
        key.setAllCaps(false);
        key.setText(label);
        key.setTextSize(TextUtils.equals(label, "SPACE") ? 14 : 12);
        key.setGravity(Gravity.CENTER);
        key.setMinHeight(0);
        key.setMinWidth(0);
        key.setPadding(dp(2), 0, dp(2), 0);
        key.setBackgroundResource(isActionKey(label) ? R.drawable.action_key_background : R.drawable.key_background);
        key.setTextColor(0xffffffff);
        key.setOnClickListener(v -> handleKey(label));

        if (TextUtils.equals(label, "SHIFT")) shiftKey = key;
        if (TextUtils.equals(label, "CTRL")) ctrlKey = key;
        if (TextUtils.equals(label, "ALT")) altKey = key;
        return key;
    }

    private float weightFor(String label) {
        if (TextUtils.equals(label, "SPACE")) return 3.0f;
        if (TextUtils.equals(label, "ENTER") || TextUtils.equals(label, "SHIFT") || TextUtils.equals(label, "BKSP")) return 1.6f;
        if (TextUtils.equals(label, "CTRL") || TextUtils.equals(label, "ALT") || TextUtils.equals(label, "TAB") || TextUtils.equals(label, "ESC")) return 1.35f;
        return 1.0f;
    }

    private boolean isActionKey(String label) {
        switch (label) {
            case "ESC":
            case "TAB":
            case "CTRL":
            case "ALT":
            case "UP":
            case "DOWN":
            case "LEFT":
            case "RIGHT":
            case "BKSP":
            case "ENTER":
            case "SHIFT":
            case "123":
            case "NEXT":
                return true;
            default:
                return false;
        }
    }

    private void handleKey(String label) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (label) {
            case "SHIFT":
                shift = !shift;
                updateModifierLabels();
                return;
            case "CTRL":
                ctrlLatch = !ctrlLatch;
                updateModifierLabels();
                return;
            case "ALT":
                altLatch = !altLatch;
                updateModifierLabels();
                return;
            case "BKSP":
                sendKey(ic, KeyEvent.KEYCODE_DEL);
                return;
            case "ENTER":
                sendKey(ic, KeyEvent.KEYCODE_ENTER);
                return;
            case "TAB":
                sendKey(ic, KeyEvent.KEYCODE_TAB);
                return;
            case "ESC":
                sendKey(ic, KeyEvent.KEYCODE_ESCAPE);
                return;
            case "UP":
                sendKey(ic, KeyEvent.KEYCODE_DPAD_UP);
                return;
            case "DOWN":
                sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN);
                return;
            case "LEFT":
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT);
                return;
            case "RIGHT":
                sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT);
                return;
            case "SPACE":
                commitText(ic, " ");
                return;
            case "NEXT":
                switchToNextInputMethod();
                return;
            case "123":
                commitText(ic, "~");
                return;
            default:
                commitText(ic, printable(label));
        }
    }

    private String printable(String label) {
        if (label.length() == 1 && Character.isLetter(label.charAt(0))) {
            return shift ? label.toUpperCase(Locale.US) : label;
        }
        return label;
    }

    private void commitText(InputConnection ic, String text) {
        if (ctrlLatch && text.length() == 1) {
            char c = Character.toLowerCase(text.charAt(0));
            if (c >= 'a' && c <= 'z') {
                sendModifiedKey(ic, keyCodeForLetter(c), KeyEvent.META_CTRL_ON);
                clearOneShotModifiers();
                return;
            }
        }

        if (altLatch && text.length() == 1) {
            char c = Character.toLowerCase(text.charAt(0));
            int keyCode = keyCodeForLetter(c);
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                sendModifiedKey(ic, keyCode, KeyEvent.META_ALT_ON);
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

    private int keyCodeForLetter(char c) {
        if (c < 'a' || c > 'z') return KeyEvent.KEYCODE_UNKNOWN;
        return KeyEvent.KEYCODE_A + (c - 'a');
    }

    private void clearOneShotModifiers() {
        ctrlLatch = false;
        altLatch = false;
        if (shift) shift = false;
        updateModifierLabels();
    }

    private void updateModifierLabels() {
        if (shiftKey != null) shiftKey.setText(shift ? "SHIFT*" : "SHIFT");
        if (ctrlKey != null) ctrlKey.setText(ctrlLatch ? "CTRL*" : "CTRL");
        if (altKey != null) altKey.setText(altLatch ? "ALT*" : "ALT");
    }

    private void switchToNextInputMethod() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            // Deprecated on newer Android, but still works across the minSdk range for a simple starter IME.
            imm.switchToNextInputMethod(getWindow().getWindow().getAttributes().token, false);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package dev.foxfire.tboard;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class SetupActivity extends Activity {
    static final String PREFS_NAME = "tboard_settings";
    static final String PREF_SPEECH_PROVIDER = "speech_provider";
    static final String SPEECH_PROVIDER_SYSTEM_DEFAULT = "";
    static final String SPEECH_PROVIDER_ON_DEVICE = "__on_device__";
    static final String RECOGNITION_SERVICE_INTERFACE = "android.speech.RecognitionService";

    private static final int REQUEST_RECORD_AUDIO = 1001;

    private RadioGroup speechProviderGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("TBoard");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText("A terminal-focused Android keyboard starter.\n\n1. Enable TBoard in system keyboard settings.\n2. Select TBoard as your current keyboard.\n3. Grant microphone permission if you want voice input.\n4. Pick a speech provider if you do not want the system default.\n5. Open Termius/tmux and try Esc, Ctrl, Tab, arrows, symbols, and Mic.");
        body.setTextSize(16);
        body.setPadding(0, dp(16), 0, dp(16));
        root.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button enable = new Button(this);
        enable.setText("Enable TBoard");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button choose = new Button(this);
        choose.setText("Choose Keyboard");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
        root.addView(choose, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button mic = new Button(this);
        mic.setText(hasMicPermission() ? "Microphone Permission Granted" : "Grant Microphone Permission");
        mic.setEnabled(!hasMicPermission());
        mic.setOnClickListener(v -> requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO));
        root.addView(mic, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        addSpeechProviderSettings(root);

        setContentView(scroll);

        if (getIntent().getBooleanExtra("requestMicPermission", false) && !hasMicPermission()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    private void addSpeechProviderSettings(LinearLayout root) {
        TextView heading = new TextView(this);
        heading.setText("Voice input provider");
        heading.setTextSize(20);
        heading.setPadding(0, dp(24), 0, dp(8));
        root.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("System Default is usually best. If punctuation is weak, try another provider such as Google if listed.");
        note.setTextSize(14);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        speechProviderGroup = new RadioGroup(this);
        speechProviderGroup.setOrientation(RadioGroup.VERTICAL);
        root.addView(speechProviderGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        String selected = getPrefs().getString(PREF_SPEECH_PROVIDER, SPEECH_PROVIDER_SYSTEM_DEFAULT);

        RadioButton systemDefault = new RadioButton(this);
        systemDefault.setText("System Default");
        systemDefault.setTag(SPEECH_PROVIDER_SYSTEM_DEFAULT);
        speechProviderGroup.addView(systemDefault);
        if (SPEECH_PROVIDER_SYSTEM_DEFAULT.equals(selected)) {
            systemDefault.setChecked(true);
        }

        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            RadioButton onDevice = new RadioButton(this);
            onDevice.setText("On-device default");
            onDevice.setTag(SPEECH_PROVIDER_ON_DEVICE);
            speechProviderGroup.addView(onDevice);
            if (SPEECH_PROVIDER_ON_DEVICE.equals(selected)) {
                onDevice.setChecked(true);
            }
        }

        List<ResolveInfo> providers = getSpeechProviders();
        for (ResolveInfo provider : providers) {
            if (provider.serviceInfo == null) continue;
            ComponentName component = new ComponentName(
                    provider.serviceInfo.packageName,
                    provider.serviceInfo.name);
            String flattened = component.flattenToString();

            RadioButton option = new RadioButton(this);
            option.setText(providerLabel(provider) + "\n" + flattened);
            option.setTextSize(14);
            option.setTag(flattened);
            speechProviderGroup.addView(option);
            if (flattened.equals(selected)) {
                option.setChecked(true);
            }
        }

        if (!SPEECH_PROVIDER_SYSTEM_DEFAULT.equals(selected)
                && speechProviderGroup.getCheckedRadioButtonId() == -1) {
            systemDefault.setChecked(true);
            saveSpeechProvider(SPEECH_PROVIDER_SYSTEM_DEFAULT);
        }

        speechProviderGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedButton = group.findViewById(checkedId);
            if (selectedButton == null) return;
            String provider = String.valueOf(selectedButton.getTag());
            saveSpeechProvider(provider);
            Toast.makeText(this, "Voice provider saved", Toast.LENGTH_SHORT).show();
        });
    }

    private List<ResolveInfo> getSpeechProviders() {
        Intent intent = new Intent(RECOGNITION_SERVICE_INTERFACE);
        return getPackageManager().queryIntentServices(intent, PackageManager.MATCH_ALL);
    }

    private String providerLabel(ResolveInfo provider) {
        CharSequence serviceLabel = provider.loadLabel(getPackageManager());
        if (serviceLabel != null) {
            return serviceLabel.toString();
        }
        return provider.serviceInfo.packageName;
    }

    private void saveSpeechProvider(String provider) {
        getPrefs().edit().putString(PREF_SPEECH_PROVIDER, provider).apply();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            recreate();
        }
    }

    private boolean hasMicPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

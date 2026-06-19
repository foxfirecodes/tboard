# TBoard

TBoard is a starter Android input method editor (custom keyboard) aimed at terminal-heavy workflows in apps like Termius and tmux.

## Current prototype

- Basic QWERTY layout
- Terminal keys: `Esc`, `Tab`, `Ctrl`, `Alt`, arrow keys, backspace, enter
- Handy symbols: `/`, `-`, `_`, `|`, `:`, `;`, `~`
- Setup screen with shortcuts to enable/select the keyboard

## Build

```bash
mise run build
# or: gradle assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Fallback SDK-only build, if Gradle dependency resolution is unavailable:

```bash
mise run build-manual
```

## Install / enable

```bash
mise run install
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open **TBoard**, tap **Enable TBoard**, enable it in Android keyboard settings, and tap **Choose Keyboard**.

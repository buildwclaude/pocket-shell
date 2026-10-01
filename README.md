# Pocket Shell

My own terminal app for Android — a real shell in your pocket.

Native Android (Kotlin), min Android 8.0. No permissions: the shell runs inside the
app's own sandbox.

## 📲 Install

1. On your phone, open the **[latest release](../../releases/latest)**.
2. Download **`PocketShell.apk`** and open it. Allow installing from this source, then **Install**.

It's a debug build (self-signed), so Android may warn about an unknown developer — expected
for a personal app installed outside the Play Store.

## How it works

```
Screen (TerminalView)  ⇄  terminal emulator  ⇄  PTY (pseudo-terminal)  ⇄  shell
```

- **TerminalView** draws the character grid and turns taps/keys into bytes.
- **Terminal emulator** understands escape codes (`\e[31m` = red, cursor moves, clear screen…).
- **PTY** is a fake terminal device created in C; the shell thinks it's talking to a real terminal.
- **Shell** runs your commands.

## Roadmap

1. ✅ Terminal running Android's built-in `/system/bin/sh`, extra-keys row, pinch-to-zoom, copy/paste
2. Bash: compiled for Android, bundled in the APK as `libbash.so` (Android 10+ only lets apps
   execute files from their native-library folder)
3. Polish: themes, multiple sessions/tabs, keep sessions alive in the background
4. Extras: SSH to my home server over Tailscale

## Building

GitHub Actions builds every push to `main` ([`.github/workflows/build.yml`](.github/workflows/build.yml))
and attaches the APK to the rolling **`latest`** release. Locally: `./gradlew assembleDebug`.

## Credits

Terminal emulation and rendering use the `terminal-emulator` and `terminal-view` libraries from
[Termux](https://github.com/termux/termux-app), licensed under
[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).

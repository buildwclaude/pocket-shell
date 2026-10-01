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

1. ✅ Terminal with an extra-keys row (Esc, Tab, Ctrl, Alt, arrows), pinch-to-zoom, copy/paste
2. ✅ **GNU bash 5.2**, cross-compiled for Android and bundled in the APK as `libbash.so`
   (Android 10+ only lets apps execute files from their native-library folder). Falls back to
   Android's `/system/bin/sh` if bash is missing.
3. Polish: themes, multiple sessions/tabs, keep sessions alive in the background
4. Extras: SSH to my home server over Tailscale

## Building

GitHub Actions ([`.github/workflows/build.yml`](.github/workflows/build.yml)) on every push to `main`:

1. **build**: cross-compiles bash with the Android NDK ([`scripts/build-bash.sh`](scripts/build-bash.sh)), then the APK
2. **test**: installs it on an Android emulator and checks the app launches, bash runs, and typed commands reach it
3. **release**: only if the test passed, attaches the APK to the rolling **`latest`** release

Locally: `ANDROID_NDK_HOME=/path/to/ndk scripts/build-bash.sh && ./gradlew assembleDebug`.

## Credits

- **GNU bash** 5.2.37, © Free Software Foundation, licensed under [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html).
  Built unmodified (one missing `#include` added) from the official source,
  <https://ftp.gnu.org/gnu/bash/bash-5.2.37.tar.gz>, checksum-pinned in `scripts/build-bash.sh`.

- Terminal emulation and rendering use the `terminal-emulator` and `terminal-view` libraries from
[Termux](https://github.com/termux/termux-app), licensed under
[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).

#!/usr/bin/env bash
# Cross-compiles GNU bash for Android with the NDK and drops it into the app as
# app/src/main/jniLibs/<abi>/libbash.so.
#
# Why "libbash.so"? Since Android 10 an app may only execute files that came out of
# its APK's native-library folder (the W^X rule). Android extracts lib*.so files from
# jniLibs into that folder, so naming the bash executable libbash.so is how it gets
# there with execute permission.
#
# bash is linked only against Android's own libc/libdl/libm. Readline and termcap
# come from bash's bundled copies, so nothing else needs to ship with it.
#
# Usage: ANDROID_NDK_HOME=/path/to/ndk scripts/build-bash.sh [abi...]
set -euo pipefail

BASH_VER=5.2.37
# GPG-verified against Chet Ramey's key (bash maintainer) when this was pinned.
BASH_SHA256=9599b22ecd1d5787ad7d3b7bf0c59f312b3396d1e281175dd1f8a4014da621ff
API=26 # same as minSdk

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/build/bash"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}"
[[ -d "$NDK" ]] || { echo "Set ANDROID_NDK_HOME to the Android NDK" >&2; exit 1; }
TOOLS="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

ABIS=("$@")
[[ ${#ABIS[@]} -gt 0 ]] || ABIS=(arm64-v8a x86_64)

mkdir -p "$WORK"
TARBALL="$WORK/bash-$BASH_VER.tar.gz"
if [[ ! -f "$TARBALL" ]]; then
    curl -fsSL -o "$TARBALL.part" "https://ftp.gnu.org/gnu/bash/bash-$BASH_VER.tar.gz"
    mv "$TARBALL.part" "$TARBALL"
fi
echo "$BASH_SHA256  $TARBALL" | sha256sum -c -

for ABI in "${ABIS[@]}"; do
    case "$ABI" in
        arm64-v8a) TRIPLE=aarch64-linux-android ;;
        x86_64) TRIPLE=x86_64-linux-android ;;
        *) echo "Unsupported ABI: $ABI" >&2; exit 1 ;;
    esac

    SRC="$WORK/$ABI/bash-$BASH_VER"
    rm -rf "$WORK/$ABI"
    mkdir -p "$WORK/$ABI"
    tar -xzf "$TARBALL" -C "$WORK/$ABI"

    (
        cd "$SRC"
        # The bundled termcap calls write() without its header; modern clang rejects
        # implicit function declarations.
        sed -i '1i #include <unistd.h>' lib/termcap/tparam.c

        # CC_FOR_BUILD compiles helper programs that run on the build machine during
        # the build; bash's old-style C needs gnu17 (GCC 15+ defaults to C23).
        # Cross-compiling means configure can't run test programs on the target, so
        # these answers about Android's libc are given up front (same as Termux's).
        ./configure \
            --host="$TRIPLE" \
            --build="$(./support/config.guess)" \
            --enable-multibyte \
            --enable-progcomp \
            --without-bash-malloc \
            --disable-nls \
            CC="$TOOLS/$TRIPLE$API-clang" \
            AR="$TOOLS/llvm-ar" \
            RANLIB="$TOOLS/llvm-ranlib" \
            CC_FOR_BUILD="cc -std=gnu17" \
            bash_cv_termcap_lib=gnutermcap \
            bash_cv_job_control_missing=present \
            bash_cv_sys_siglist=yes \
            bash_cv_func_sigsetjmp=present \
            bash_cv_unusable_rtsigs=no \
            bash_cv_dev_fd=whacky \
            bash_cv_getcwd_malloc=yes \
            ac_cv_func_mbsnrtowcs=no \
            > "$WORK/$ABI/configure.log"
        make -j"$(nproc)" bash > "$WORK/$ABI/make.log"
    )

    mkdir -p "$ROOT/app/src/main/jniLibs/$ABI"
    "$TOOLS/llvm-strip" -o "$ROOT/app/src/main/jniLibs/$ABI/libbash.so" "$SRC/bash"
    echo "Built $ABI: $(du -h "$ROOT/app/src/main/jniLibs/$ABI/libbash.so" | cut -f1)"
done

{ pkgs, config, lib, inputs, ... }:

let
  androidSdk = inputs.android-nixpkgs.sdk.${pkgs.system} (sdkPkgs: with sdkPkgs; [
    cmdline-tools-latest
    platform-tools
    build-tools-36-0-0
    platforms-android-37-0
    ndk-28-2-13676358
  ]);
  androidHome = "${androidSdk}/share/android-sdk";
  ndkHome = "${androidHome}/ndk/28.2.13676358";
  # User-home properties apply to the IDE Tooling API too, unlike GRADLE_OPTS.
  gradleProperties = pkgs.writeText "tinygram-gradle.properties" ''
    android.aapt2FromMavenOverride=${androidHome}/build-tools/36.0.0/aapt2
    org.gradle.java.installations.paths=${pkgs.jdk17.home},${pkgs.jdk25.home}
    org.gradle.java.installations.auto-detect=false
    org.gradle.java.installations.auto-download=false
  '';
in
{
  packages = with pkgs; [
    androidSdk
    cargo-ndk
    git
    gnumake
    pass
    shellcheck
    stdenv.cc
    watchexec
  ];

  languages.c.enable = lib.mkForce false;

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
    lsp.enable = false;
  };

  languages.rust = {
    enable = true;
    channel = "stable";
    version = "1.98.0";
    components = [ "rustc" "cargo" "clippy" "rustfmt" ];
    targets = [
      "aarch64-linux-android"
      "armv7-linux-androideabi"
      "x86_64-linux-android"
    ];
    lsp.enable = false;
  };

  env = {
    ANDROID_HOME = androidHome;
    ANDROID_SDK_ROOT = androidHome;
    ANDROID_NDK_HOME = ndkHome;
    ANDROID_NDK_ROOT = ndkHome;
    # The versioned daemon JVM criteria require 25 while JAVA_HOME supplies the launcher on 17.
    JDK25_HOME = pkgs.jdk25.home;
    # Native Gradle tasks prepend CARGO_HOME/bin, which must not contain host rustup proxies.
    CARGO_HOME = "${config.devenv.state}/cargo";
    GRADLE_USER_HOME = "${config.devenv.state}/gradle";
  };

  scripts.tinygram-configure = {
    description = "Configure local Android SDK and Telegram credentials from pass";
    exec = ''
      exec bash ${lib.escapeShellArg "${config.devenv.root}/scripts/configure-dev.sh"} "$@"
    '';
  };

  enterShell = ''
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/36.0.0:$PATH"
    mkdir -p "$CARGO_HOME" "$GRADLE_USER_HOME"
    ln -sfn ${gradleProperties} "$GRADLE_USER_HOME/gradle.properties"
  '';

  enterTest = ''
    set -euo pipefail
    java -version
    "$JDK25_HOME/bin/java" -version
    rustc --version
    cargo ndk --version
    watchexec --version
    adb version
    aapt2 version
    test -f "$ANDROID_HOME/platforms/android-37.0/android.jar"
    test -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
    for target in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; do
      test -d "$(rustc --print target-libdir --target "$target")"
    done
    shellcheck ${lib.escapeShellArg "${config.devenv.root}/scripts/configure-dev.sh"} ${lib.escapeShellArg "${config.devenv.root}/scripts/tests/configure-dev-test.sh"}
    bash ${lib.escapeShellArg "${config.devenv.root}/scripts/tests/configure-dev-test.sh"}
  '';
}

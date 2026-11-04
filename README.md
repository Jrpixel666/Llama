# Llama AI Studio

A native Android app (Kotlin + Jetpack Compose + NDK/C++) built for the Google Pixel 9 /
Tensor G4, combining fully local LLM inference (llama.cpp), a Hugging Face model browser,
a notes workspace, and a lightweight Android dev workspace.

## Workspaces

- **Chat** — offline chat backed by a vendored, pinned build of llama.cpp
  (`third_party/llama.cpp`, see `VENDORED_COMMIT.txt`). Streaming generation, adjustable
  temperature/top-k/top-p/min-p/repeat-penalty/context length/max tokens, live tok/s, and
  persistent sessions (Room).
- **Notes** — Markdown notes with folders, search, autosave, AI summaries (runs on the
  loaded local model), and two-way handoff with Chat (send a note into chat context, save a
  chat reply as a note). Side-by-side layout on wide screens (`BoxWithConstraints`).
- **Models** — Hugging Face search (`huggingface.co/api/models`), GGUF file listing with
  quantisation/param-count/size parsed from the API's own `gguf` metadata and filenames,
  resumable downloads (HTTP Range) with SHA-256 verification against HF's published LFS
  hash, and a local model library.
- **Performance** — live memory and thermal-headroom monitoring, a performance-profile
  picker (battery saver / balanced / max performance, backed by DataStore), manual
  thread/context tuning, and a benchmark that measures real prefill/generation tok/s on the
  loaded model (not an estimate).
- **Dev Studio** — project scaffolding, a file explorer, and a plain-text/monospace code
  editor for Kotlin/Java/XML, all stored in app-private storage. See **Dev Studio and ARM64**
  below for why on-device APK building is scoped as experimental.

## Native engine

`app/src/main/cpp/llama_bridge.cpp` is a JNI bridge over llama.cpp's modern C API
(`llama_model_load_from_file`, sampler chains, `llama_chat_apply_template`, etc.). It is
built CPU-only for this ABI:

- ARM64 NEON-optimized ggml CPU kernels (ggml's own cross-platform SIMD dispatch).
- **KleidiAI** micro-kernels (`GGML_CPU_KLEIDIAI=ON` for `arm64-v8a`), matching the ggml
  upstream's own `examples/llama.android` reference project.
- OpenMP threading (also per the upstream Android reference).

**Vulkan is not compiled into this build.** `ggml-vulkan`'s shader compiler
(`vulkan-shaders-gen`) is built as a target-ABI binary by llama.cpp's own CMake, which
cannot run on the x86-64 host doing the cross-compile — making it work requires patching
the build to compile that one tool for the host separately. That's a real, scoped follow-up,
not a hand-wave: `LlamaNative.supportsVulkan()` and the whole backend-selection UI are wired
up and will report `true` the day a build defines `GGML_USE_VULKAN`; until then the app is
explicit in the Performance tab that it's running CPU/NEON/KleidiAI only, rather than
claiming GPU acceleration it doesn't have.

## Dev Studio and ARM64

Google's own Android SDK `build-tools` (`aapt2`, `d8`, `zipalign`) are published for
linux-x86_64, macOS and Windows hosts only — there is no linux-aarch64 build. That means a
*normal* Android build (the one that produced this APK) always runs on an x86-64 machine,
and there is no official, supported way to run those exact binaries on the ARM64 phone
itself. Dev Studio's toolchain panel is honest about this (see
`app/src/main/java/com/llamaaistudio/app/devstudio/TermuxBridge.kt`): it documents which
components are genuinely ARM64-native (Termux's own aarch64 OpenJDK, Gradle, Git, Kotlin
compiler) and which are not, and routes on-device building through Termux rather than
silently assuming Google's x86-64 binaries would run on the device. Plain JVM
(Kotlin/Java) compilation that way is solid; full Android APK assembly via Termux is
labelled experimental in the UI because AGP's own `aapt2` dependency still expects the x86
artifact unless substituted.

## Building this APK yourself

```
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
    "ndk;27.2.12479018" "cmake;3.22.1"
echo "sdk.dir=/path/to/sdk" > local.properties
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (arm64-v8a only, debug-signed).

For a release build, add a `keystore.properties` at the repo root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) — release signing is otherwise
skipped and `assembleRelease` produces an unsigned APK.

## What's verified vs. what isn't

Verified in this build: it compiles clean, the arm64-v8a native libraries
(`libggml*.so`, `libllama*.so`, `libllama_bridge.so`) are present and correctly targeted,
the APK is validly signed and zip-aligned (`apksigner verify`, `zipalign -c`). What is
**not** verified here: there is no Pixel 9 (or any ARM64 device/emulator — this build
environment has no `/dev/kvm`) available to actually run inference and measure real
tokens/sec on hardware. The Performance tab's benchmark is real code that will report real
numbers the first time someone runs it on a device; no numbers are fabricated in this
repo or its docs.

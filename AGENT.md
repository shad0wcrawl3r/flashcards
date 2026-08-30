# AGENT.md

Notes for whichever agent picks this project up next.

## What this is

An Android (Jetpack Compose) flashcard app, built for personal certification-exam prep (the
sample content in this repo leans Kubernetes/CKA-style — see the field-selector example below).
Decks of Q/A and multiple-choice cards, deck import/export/share via a keyless pastebin + QR
code, and — as of this session — in-progress work adding speech (TTS/STT) support and on-device
LLM-based answer grading.

## Build & dev environment

- **No system JDK is on `PATH`** in the usual dev environment for this project — bare
  `./gradlew` fails with "JAVA_HOME is not set". Android Studio is installed at
  `/opt/android-studio` and bundles its own JDK at `/opt/android-studio/jbr` (confirmed: OpenJDK
  25.0.2). Prefix every gradle invocation:
  ```bash
  JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:compileDebugKotlin
  JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug
  JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:installDebug
  JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest
  ```
- Android SDK path is already set in `local.properties` (gitignored, not committed) —
  `sdk.dir=<path to>/Android/Sdk`. `platform-tools` (`adb`) is expected to already be on `PATH`.
- If a device/emulator is connected, **`adb shell input tap/swipe/...` is blocked** in at least
  some dev setups — it throws `SecurityException: ... requires INJECT_EVENTS permission`. Don't
  assume you can drive the UI that way. Use Compose instrumented tests (`app/src/androidTest`,
  run via Gradle) for programmatic UI interaction, or ask the human to test manually on-device.
  Verify this still holds in your own environment rather than assuming — it may be specific to
  a particular sandboxing setup.

## Architecture at a glance

No repository layer — `ViewModel`s talk to the Room `FlashcardDatabase` directly. Pattern to
copy for a new screen: a `ViewModel` whose constructor takes `database` (and any other deps),
exposing `StateFlow`s, plus a matching `ViewModelProvider.Factory` in the same file (see
`ui/DeckListViewModel.kt`, `ui/FlashCardViewModel.kt`).

- `MainActivity.kt` — the Compose Navigation graph, plus the practice/flashcard screen itself
  (`FlashcardScreen`: QA/MCQ card rendering, swipe-to-advance, TTS speaker button).
- `ui/DeckSelectionScreen.kt` — deck list, search, multi-select (share/delete/edit), and the
  settings hamburger drawer (all speech + LLM settings live here, slide-out panel).
- `ui/DeckEditorScreen.kt`, `ui/McqCardEditor.kt` — deck/card authoring.
- `ui/ImportScreen.kt` + `data/DeckImporter.kt` — import via URL / QR scan / file.
- `data/PastebinService.kt` + `ui/ShareViewModel.kt` — share decks via paste.rs + generated QR.
- `model/` — Room entities/DAOs (`Deck`, `Flashcard`, `FlashcardDatabase`).

## Recent work (2026-08-30): speech support + on-device LLM answer grading

Motivating problem: while practicing Q/A cards by speaking the answer out loud, you need to
judge whether the spoken answer is "close enough" to the canonical answer. Plain string/keyword
similarity fails on real paraphrases — e.g. question "What is a field selector?", canonical
answer "A query mechanism that filters Kubernetes resources by resource fields", spoken answer
"something that matches against the fields set in the spec or manifest": a reasonable paraphrase
with almost no shared vocabulary. Decided to build two routes in parallel rather than commit to
one up front:

1. **Self-assessed (primary, shipped).** QA cards already work this way — tap to reveal the
   answer, swipe to continue, no auto-grading. TTS was added on top: `ui/TtsController.kt` wraps
   Android's `TextToSpeech` (queues an utterance if it fires before the engine's async init
   completes — otherwise the very first card's auto-read gets silently dropped). A speaker
   button sits on every practice card; when the TTS toggle (settings drawer) is on, each new
   card/answer-reveal auto-reads without needing the button; when off, the button stays for
   on-demand playback either way.

2. **On-device LLM judge (experimental — framework built, not wired into real grading yet).**
   - `ui/SpeechRecognitionController.kt` — on-device `SpeechRecognizer` STT wrapper, exercised by
     a sandbox in the settings drawer (mic button, live partial/final transcript). Needs
     `RECORD_AUDIO` (declared in the manifest, requested at runtime).
   - `ui/LlmController.kt` — wraps MediaPipe `com.google.mediapipe:tasks-genai:0.10.35`
     (`LlmInference` / `LlmInferenceSession`). `generate(prompt, temperature, topK, randomSeed)`
     opens a **fresh session per call** so sampling is explicit and reproducible, rather than
     relying on the engine's implicit default session.
   - `ui/LlmModelStore.kt` — scans `context.getExternalFilesDir(null)/llm_models/` for
     `.task`/`.litertlm` files (app-specific external storage, no runtime permission needed).
   - `ui/HuggingFaceModelService.kt` + `ui/ModelDownloader.kt` +
     `ui/ModelDownloadViewModel.kt`/`ModelDownloadScreen.kt` — in-app model search, scoped to the
     `litert-community` Hugging Face org (pre-converted, MediaPipe-compatible models only, so
     search results are guaranteed to be the right format), and a streaming download with
     progress + cancel, writing straight into the `llm_models/` dir — no PC download + `adb push`
     round-trip needed. Gated models (all of Gemma) need a Hugging Face access token — entered on
     the download screen, persisted via `SettingsViewModel.huggingFaceToken` (**plain
     SharedPreferences, not encrypted** — a deliberate scope tradeoff for a personal read-scoped
     token; don't copy this pattern for anything more sensitive). The user still has to accept
     the model's license on huggingface.co in a browser once, per repo — that's an account/login
     action no agent can do on their behalf.
   - `ui/LlmBenchmarkScreen.kt` / `LlmBenchmarkViewModel.kt` — pick a downloaded model, load it,
     run a judge prompt with configurable temperature/topK/seed, repeat it N times (seed,
     seed+1, seed+2…) to see the actual spread of answers. Each run shows latency plus a parsed
     `VERDICT` badge; running more than once shows a consistency summary ("Consistent across 3
     runs: CORRECT" / "Inconsistent across runs: 2× CORRECT, 1× INCORRECT").

## Findings to build on, not re-derive

- **Model choice: `gemma-3-270m-it`.** Small, fast, Gemma family (confirmed-compatible tokenizer
  — see below). The plan if its out-of-the-box grading quality isn't good enough is to **fine-tune
  this specific model**, not shop for a different base model first.
- **Gemma works, Qwen doesn't.** `litert-community/Qwen3.5-0.8B` — an official, correctly-sourced
  `litert-community` conversion, not a sketchy third-party one — fails to load with
  `INVALID_ARGUMENT: Sentencepiece tokenizer not found in model`. Gemma models use a
  SentencePiece tokenizer; Qwen uses a different (BPE-style) one. The native engine
  (`libllm_inference_engine_jni.so`, confirmed via `strings`) has some
  `LitertLmLoader::GetHuggingFaceTokenizer` code path, but it isn't fully wired up in
  `tasks-genai` 0.10.35 (the latest published version at the time this was checked — there was no
  newer version to try bumping to). Treat non-Gemma / non-SentencePiece model families as
  unsupported until this is re-verified against a newer engine release.
- **Sampling noise looks like model unreliability if you don't control for it.** The engine's
  implicit default session samples at temperature 0.8; a single run flip-flopped
  MATCH→NO_MATCH on an *identical* prompt purely from that, not from the model actually being
  bad at the task. `topK = 1` forces greedy/deterministic decoding regardless of temperature or
  seed — use it first to separate "the model is unreliable" from "sampling got unlucky" before
  drawing conclusions about a model's quality.
- **Keep the judge prompt in a labeled, parseable format** (`VERDICT:` / `CONFIDENCE:` /
  `REASON:` lines) rather than reverting to freeform prose — that's what makes the Runs
  consistency-check in the benchmark screen actually work. `CONFIDENCE` is there so you can tell
  "the model is unsure" apart from "the model is confidently wrong" when repeated runs disagree.
- **Don't trust `tasks-genai`'s official Kotlin doc snippets at face value.** The public guide
  (ai.google.dev/edge/mediapipe/.../llm_inference/android) shows `LlmInferenceOptions.Builder`
  with `.setTopK()`/`.setTemperature()`/`.setRandomSeed()` — those methods don't exist there in
  0.10.35 (confirmed by decompiling the actual AAR from the Gradle cache and running `javap` on
  it). Those params moved to `LlmInferenceSession.LlmInferenceSessionOptions.Builder`. Before
  writing code against a new version of this dependency, decompile and check rather than
  trusting the sample code.

## Not done yet

- Fine-tuning `gemma-3-270m-it` for answer-grading — explicitly deferred ("for later") by the
  user in this session.
- Wiring the LLM judge into actual practice-mode grading. It currently only exists as a
  standalone benchmark/sandbox tool reachable from the settings drawer — nothing in the normal
  practice flow (`FlashcardScreen`) calls it yet.
- The "on-device speech recognition" toggle in the settings drawer is currently just a persisted
  preference bit (`SettingsViewModel.onDeviceRecognitionEnabled`) with no behavior wired to it —
  the recognition sandbox works independently of whether this toggle is on.

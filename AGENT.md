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
- If a device/emulator is connected, **`adb shell input tap/swipe/...` may or may not be
  blocked — it's per-device, not a project-wide constant.** One test device threw
  `SecurityException: ... requires INJECT_EVENTS permission` on every input event; a different
  one (`CPH2569`) accepted taps/swipes fine over the same adb connection. Check with a throwaway
  `adb shell input tap 0 0` before relying on it either way, rather than assuming from a prior
  session. When it does work and the connected device is a real personal phone (not an
  emulator), stay inside the app under test — don't navigate elsewhere on the home screen or
  poke at other installed apps. Where it's blocked, use Compose instrumented tests
  (`app/src/androidTest`, run via Gradle) for programmatic UI interaction, or ask the human to
  test manually.

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
   - `ui/LlmController.kt` — wraps `com.google.ai.edge.litertlm:litertlm-android:0.16.1`
     (`Engine` / `Session`). `generate(prompt, temperature, topK, randomSeed)` opens a **fresh
     raw `Session` per call** (not the chat-templated `Conversation` API) so sampling is explicit
     and reproducible and nothing leaks between calls. Migrated off MediaPipe's LLM Inference API
     on 2026-08-31 — that API is now deprecated/maintenance-only; LiteRT-LM is Google's named
     successor and adds support for model families beyond Gemma's SentencePiece tokenizer.
   - `ui/LlmModelStore.kt` — scans `context.getExternalFilesDir(null)/llm_models/` for
     `.litertlm` files (app-specific external storage, no runtime permission needed). MediaPipe's
     old `.task` format is no longer accepted — the new engine can't load it.
   - `ui/HuggingFaceModelService.kt` + `ui/ModelDownloader.kt` +
     `ui/ModelDownloadViewModel.kt`/`ModelDownloadScreen.kt` — in-app model search, scoped to the
     `litert-community` Hugging Face org (pre-converted, LiteRT-LM-compatible `.litertlm` models
     only, so search results are guaranteed to be the right format), and a streaming download with
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

## Recent work (2026-08-31): fixed STT "Unknown recognition error (12)"

Reported on a new test device (`CPH2569`, system locale `en-NP`): the recognition sandbox threw
error 12 with no useful message. Root cause, confirmed live via `adb logcat` on the device:
`SpeechRecognizer.checkRecognitionSupport()` reported `installed=[]` — **zero on-device speech
language packs downloaded at all**, against 31 languages the recognizer service *supports*. Not
a locale-mismatch edge case specifically; this device simply never had any pack downloaded, and
`startListening()` never set `EXTRA_LANGUAGE`, so it silently inherited the (unsupported) system
locale and got `ERROR_LANGUAGE_NOT_SUPPORTED`.

`ui/SpeechRecognitionController.kt` now:
- Calls `checkRecognitionSupport()` before starting, and picks a language that's actually
  installed on-device rather than trusting the system locale.
- If nothing's installed, calls `SpeechRecognizer.triggerModelDownload()` to request the pack
  instead of guaranteeing a failed listen attempt, and surfaces an actionable message
  ("requested a download for 'en-US' — try again shortly").
- Fills in `describeError()` for error codes 10-15 (`ERROR_TOO_MANY_REQUESTS`,
  `ERROR_SERVER_DISCONNECTED`, `ERROR_LANGUAGE_NOT_SUPPORTED`, `ERROR_LANGUAGE_UNAVAILABLE`,
  `ERROR_CANNOT_CHECK_SUPPORT`, `ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS`), which weren't mapped
  before — that's why 12 fell through to "Unknown recognition error (12)".
- Logs `onError` and the `checkRecognitionSupport` outcome via `Log.w`/`Log.d` under tag
  `"SpeechRecognition"` (`adb logcat -s SpeechRecognition`).
- Uses the callback-based `SpeechRecognizer.triggerModelDownload(Intent, Executor,
  ModelDownloadListener)` overload — **not** the plain `triggerModelDownload(Intent)` one, which
  is fire-and-forget with no progress or completion signal anywhere (confirmed on-device: a real
  download completed silently, with nothing in the system Downloads notification or the app —
  the only sign was that recognition started working after the device was unlocked later).
  `android.speech.ModelDownloadListener` is a **top-level class in `android.speech`, not a nested
  class of `SpeechRecognizer`** (`SpeechRecognizer.ModelDownloadListener` doesn't compile —
  confirmed by decompiling `android.jar` from the SDK platform with `javap`, same lesson as the
  MediaPipe docs below: don't trust a plausible-looking qualified name, check the actual class).
  The sandbox now shows `onProgress`/`onSuccess`/`onScheduled`/`onError` as a live status message.

**Fully verified end-to-end on the reporting device**, including the previously-open question
of whether the triggered download actually completes: it does — recognition started working
after the device was unlocked following a background download. The gap was purely *visibility*
(nothing surfaced the download happening or finishing), which the `ModelDownloadListener`
wiring above now fixes.

## Findings to build on, not re-derive

- **Model choice: `gemma-3-270m-it`.** Small, fast, Gemma family (confirmed-compatible tokenizer
  — see below). The plan if its out-of-the-box grading quality isn't good enough is to **fine-tune
  this specific model**, not shop for a different base model first.
- **Gemma-vs-Qwen tokenizer limitation was MediaPipe-specific, now resolved by migrating to
  LiteRT-LM (2026-08-31) — not yet re-verified on-device.** Under the old MediaPipe engine
  (`tasks-genai` 0.10.35), loading `litert-community/Qwen3.5-0.8B` — an official, correctly-sourced
  conversion, not a sketchy third-party one — failed with `INVALID_ARGUMENT: Sentencepiece
  tokenizer not found in model` (Gemma uses SentencePiece; Qwen uses a different BPE-style
  tokenizer that engine version didn't fully support). LiteRT-LM's own docs list Gemma, Llama,
  Phi-4, and Qwen as supported, so this should now be fixed, but nobody has actually loaded a
  Qwen `.litertlm` file against the new engine yet — treat that as unverified until someone does.
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
- **`litertlm-android`'s published Kotlin docs are accurate** (unlike the old MediaPipe
  `tasks-genai` guide, whose sample code didn't match its real 0.10.35 API surface). For anything
  the `docs/api/kotlin/getting_started.md` guide doesn't cover, the actual source is public at
  `raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/kotlin/java/com/google/ai/edge/litertlm/*.kt`
  — read that rather than guessing. Either way, verify any dependency/API change by actually
  running `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug`.

## Not done yet

- Fine-tuning `gemma-3-270m-it` for answer-grading — explicitly deferred ("for later") by the
  user in this session.
- Wiring the LLM judge into actual practice-mode grading. It currently only exists as a
  standalone benchmark/sandbox tool reachable from the settings drawer — nothing in the normal
  practice flow (`FlashcardScreen`) calls it yet.
- The "on-device speech recognition" toggle in the settings drawer is currently just a persisted
  preference bit (`SettingsViewModel.onDeviceRecognitionEnabled`) with no behavior wired to it —
  the recognition sandbox works independently of whether this toggle is on.

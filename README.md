# androidkit

**sysl on Android, drawn by SDL3.** A phone runs an ordinary sysl program: `sysl build-c` compiles it
to an archive, CMake links that into the `.so` the APK carries, and `SDLActivity` loads it.

This is a **program, not a package**. Nothing imports it. `picokit` says the same thing about a
carrier board — *"This is a program, not a package. Nothing imports it"* — and this is that
arrangement moved to a phone: the manifest, the Gradle build, the activity SDL needs on the Java
side, and the CMake that joins the two halves. Everything worth reusing is in the packages it names.

```
sysl-lang/sdl3      windows, rendering, events and input — the same binding a desktop program uses
```

## The whole program

```sysl
@export("SDL_main")
sdl_main(argc: i32, argv: **u8) -> i32
    …
```

**The interesting thing about it is its name.** Android has no `main`: `SDLActivity` loads
`libmain.so` and looks up the symbol `SDL_main` in it, so what an Android program needs is not an
entry point that runs first but one the Java half can find. `@export` publishes exactly that
(`15 §12`).

**So there is no C in this repository at all.** Every other SDL project carries a shim —
`int SDL_main(int argc, char **argv) { return main(argc, argv); }` — because a C program's entry
point is called `main` and SDL wants a different name. sysl can define the symbol directly, and the
shim has nothing left to do.

## Building it

You need Android Studio's SDK with the **NDK** and **CMake** installed (SDK Manager → SDK Tools), and
`ANDROID_HOME` set:

```
export ANDROID_HOME=~/Library/Android/sdk
./fetch-sdl3.sh
./gradlew assembleDebug
```

`fetch-sdl3.sh` downloads SDL3's official Android release into `app/libs/`. It is not committed: 16 MB
of binaries built against an NDK and an API level somebody else chose, and what belongs in a
repository is something a person can read. picokit makes the same call about its pico-sdk clone.

Then install it:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n sh.sysl.androidkit/.MainActivity
```

**Gradle needs a JDK between 17 and 25.** Android Studio uses its own bundled one and is unaffected;
a terminal whose `java` is newer needs `JAVA_HOME=<a jdk 17-25> ./gradlew assembleDebug`.

**It needs a sysl that knows `aarch64-android`, which means newer than 0.0.61.** `sysl targets`
lists the machines a compiler has; if `aarch64-android` is not among them, the build stops at
`unknown target` and no amount of Android configuration will help.

## Usage is clone-and-edit

There is no `sysl new`. Copy the repository, rename the inner `androidkit/` directory and the `name`
in its `package.hocon`, set `applicationId` and `namespace` in `app/build.gradle.kts`, move
`MainActivity.kt` to match, and write `androidkit/main.sysl`. picokit set that precedent and this
follows it.

## How the two halves are joined

**Gradle owns the link, so the arrangement is upside down from an ordinary sysl build.** picokit's
README puts it exactly: *"The pico-sdk owns the link … So the arrangement is the other way up:
`sysl build-c` compiles this program into an archive, and CMake links it."* Android is the same shape
with a different owner.

| file | what it does |
|---|---|
| `androidkit/main.sysl` | the program — exports `SDL_main` |
| `app/src/main/cpp/CMakeLists.txt` | runs `sysl build-c`, links the archive into `libmain.so` |
| `app/src/main/kotlin/…/MainActivity.kt` | an empty `SDLActivity` subclass, so the manifest has a class to name |
| `app/build.gradle.kts` | `prefab true`, one ABI, the pinned NDK |
| `fetch-sdl3.sh` | downloads the AAR |

**Four things in there are load-bearing and silent when wrong**, which is the whole reason this
section exists:

- **`-u SDL_main` in the CMake link options.** An archive member is pulled in only to resolve a
  symbol something already needs, and nothing in the `.so` calls `SDL_main` — Java looks it up by
  name at run time, long after the link. Without it the library builds, links, contains none of the
  program, and fails at `dlsym`.
- **The library must be called `main` and the symbol `SDL_main`.** They are `SDLActivity`'s defaults
  (`getMainSharedObject()`, `getMainFunction()`), and matching them is what keeps the two halves
  agreeing with nothing configured in between.
- **`--include-path sdl3=<dir>`, named rather than bare.** `sh.sysl.sdl3` declares a `pkg_config`
  requirement, and a cross build cannot run pkg-config for another machine. A bare `--include-path`
  adds a search path without answering the requirement, and the build stops with the same message.
- **`ANDROID_NDK_ROOT` is passed to `sysl build-c`.** sysl takes the newest NDK it can find and AGP
  uses the `ndkVersion` pinned in `app/build.gradle.kts` — and a machine normally has two, because
  AGP downloads its own. Handing sysl the NDK CMake is already using makes them the same by
  construction.

## What the demo is careful about, and why each one bit

The bouncing square is four lines of arithmetic. Everything around it is a phone being different
from a desktop, and each of these was found by watching it be wrong:

- **`WINDOW_RESIZABLE` plus the `SDL_ORIENTATIONS` hint.** SDL picks the activity's orientation from
  the window it is asked for — wider than tall means landscape — so a desktop-shaped request turns a
  phone sideways. Naming all four orientations is not enough on its own: `SDLActivity`'s
  `setOrientationBis` only promotes "both allowed" to *follow the device* when the window is
  **resizable**. The two together are what make it `FULL_USER`.
- **`window.safe_area()`, asked every frame.** From API 35 an Android app draws edge to edge whether
  it asks to or not, so the window runs under the status bar and the gesture bar. A program bouncing
  against the window's own size sends its contents behind both — correctly drawn and invisible. The
  alternative is `window.set_fullscreen(true)`, which hides the bars and hands the program the glass;
  that is what a game wants, and it makes the safe area the whole window, so code written against the
  safe area stays right either way.
- **`android:theme` with `Theme.NoTitleBar.Fullscreen`.** An action bar over the top means SDL's
  surface starts below it, and everything drawn is offset by a bar height the program was never told
  about.
- **A time step in seconds, clamped.** The square moves at the same speed on a 60 Hz phone and a
  120 Hz one, and an app sent to the background does not come back with the square through a wall.

## One ABI

`arm64-v8a`, and that is not an apology. On an Apple Silicon host the emulator runs `arm64-v8a`, and
so does every Android device made since 2015 — one build covers both. `x86_64` matters only on an
Intel host or a CI runner, and `armeabi-v7a` only for pre-2015 hardware; either is a line in
`app/build.gradle.kts` and a registry row in the compiler that does not exist yet.

## Not a framework, and not yet named

`sdl3` + sysl + one shell per surface is framework-shaped, and the layer that would carry a name —
the surface, input and lifecycle abstraction — does not exist. `picokit` hand-wires it for a panel
and this hand-wires it for a phone. **Extracting it from one surface would be a guess**; it gets
extracted when a second one makes the duplication visible. `androidkit` keeps its name either way: a
template is named for what it starts, which is why `picokit` is named for a board.

## License

ISC. See `LICENSE`.

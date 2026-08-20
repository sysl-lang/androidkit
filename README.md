# androidkit

**sysl on Android, drawn by SDL3.** A phone runs an ordinary sysl program: `sysl build-c` compiles it
to an archive, CMake links that into the `.so` the APK carries, and `SDLActivity` loads it.

This is a **program, not a package**. Nothing imports it. `picokit` says the same thing about a
carrier board — *"This is a program, not a package. Nothing imports it"* — and this is that
arrangement moved to a phone: the manifest, the Gradle build, the activity SDL needs on the Java
side, and the CMake that joins the two halves. Everything worth reusing is in the packages it names.

```
sysl-lang/sdl3      windows, rendering, events and input — the same binding a desktop program uses
sysl-lang/box2d     rigid-body physics — Box2D v3, vendored, and not one line of it Android-aware
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

You need Android Studio's SDK with the **NDK** and **CMake** installed (SDK Manager → SDK Tools),
**`sbt`** on the path, and `ANDROID_HOME` set:

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
`MainActivity.scala` to match, and write `androidkit/main.sysl`. picokit set that precedent and this
follows it.

## How the two halves are joined

**Gradle owns the link, so the arrangement is upside down from an ordinary sysl build.** picokit's
README puts it exactly: *"The pico-sdk owns the link … So the arrangement is the other way up:
`sysl build-c` compiles this program into an archive, and CMake links it."* Android is the same shape
with a different owner.

| file | what it does |
|---|---|
| `androidkit/main.sysl` | the program — exports `SDL_main` and the JNI entry point |
| `androidkit/bars/bars.sysl` | where the system-bar insets live — a separate module for a compiler bug |
| `app/src/main/cpp/CMakeLists.txt` | runs `sysl build-c`, links the archive into `libmain.so` |
| `activity/src/main/scala/…/MainActivity.scala` | an `SDLActivity` subclass in **Scala**, which reads the system-bar insets |
| `activity/build.sbt` | compiles it — AGP has no Scala support, so sbt does and Gradle takes the jar |
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
- **The system bars, read on the Java side and handed over through JNI.** From API 35 an Android app
  draws edge to edge whether it asks to or not, so the window runs under the status bar and the
  navigation bar, and a program bouncing against the window's own size sends its contents behind both
  — correctly drawn and invisible.

  **`window.safe_area()` is the obvious answer and it is the wrong rectangle for a drawing.** SDL
  builds it out of five inset types at once — `systemBars`, `systemGestures`,
  `mandatorySystemGestures`, `tappableElement`, `displayCutout` — because it answers *where can a
  button go*. Measured on a gesture-navigation phone that is **78 pixels off each side** for the
  back-gesture strips and a bottom inset reaching **21 pixels above** the navigation bar. For a
  picture all of it is too conservative: nothing is touchable and the sides are perfectly visible.

  SDL exposes only the combined rectangle, so `MainActivity` reads
  `WindowInsets.Type.systemBars()` and calls a native method **defined in sysl** — which is what the
  `@export("Java_sh_sysl_androidkit_MainActivity_nativeSetSystemBars")` in `main.sysl` is. The third
  option is `window.set_fullscreen(true)`, which hides the bars and hands the program the glass;
  that is what a game wants, and then every rectangle here is the whole window.
- **`android:theme` with `Theme.NoTitleBar.Fullscreen`.** An action bar over the top means SDL's
  surface starts below it, and everything drawn is offset by a bar height the program was never told
  about.
- **A time step in seconds, clamped.** The square moves at the same speed on a 60 Hz phone and a
  120 Hz one, and an app sent to the background does not come back with the square through a wall.

## Neither package knew it was on a phone

The demo is Box2D physics drawn by SDL3: a boxful of discs, squares and triangles with **no gravity**,
perfect restitution and no friction, so nothing ever settles. Tap to throw in another.

**Neither `sh.sysl.sdl3` nor `sh.sysl.box2d` needed a line changed to run here**, and that is the
claim the demo exists to make:

- **box2d** is vendored Box2D v3 — portable C with `requires { heap, posix }`, both of which Bionic
  answers. The NDK compiles it like any other C: 40 archive members, 660 `b2*` symbols.
- **sdl3** is identical on a phone because `15 §8` says a `@link` directive names a *library* and
  never a path. Only where the library lives changes, and that is `CMakeLists.txt`'s business.
- **Taps need no finger handling.** SDL synthesises mouse events from touch, so
  `EventKind.MouseButtonDown` with `mouse_x`/`mouse_y` is the whole of the input — and the same
  source runs on a desktop.

**One thing did have to change, and it was the compiler.** An Android program is always loaded as a
`.so`, and a vendored library's globals are ordinary C globals — preemptible — so `ld.lld` refused
them:

```
ld.lld: error: relocation R_AARCH64_ADR_PREL_PG_HI21 cannot be used against symbol
  'b2AssertHandler'; recompile with -fPIC
```

`Toolchain.compileC` passed `-fPIC` nowhere. `targets.md § Android` had concluded no relocation model
was needed, which was measured on sysl's *own* object — where every global is `Linkage.Private` and so
not preemptible — and did not cover the C a package carries. Fixed in the compiler, not here.

## The Java half is Scala

**There is no Java in this repository and no Kotlin either.** `MainActivity` is Scala 3, and the
Scala standard library is a dependency of the application — so an activity here can use the language
and not only its syntax. The demo proves it rather than claiming it: the insets are logged through a
`List`, a `zip`, a `map` and an interpolated string, none of which links without the runtime.

**What it costs is a second build system, and that is the whole of the cost.** The Android Gradle
plugin compiles Java and Kotlin itself and has no Scala support, so `activity/` is an sbt project and
`app/build.gradle.kts` runs it as a task and puts the jar on the classpath. `./gradlew assembleDebug`
is still the one command; `sbt` has to be installed.

**Two things bite, and both are silent:**

- **A `private` `@native` method does not work.** Scala renames a private method reached from an
  inner class to `sh$sysl$androidkit$MainActivity$$nativeSetSystemBars` so the inner class can see
  it, and JNI then looks for a symbol with `_00024` in it that nothing defines. It compiles, links,
  and dies at the first call with an `UnsatisfiedLinkError`. The listener is an inner class, so this
  is exactly that case — leave the method non-private.
- **`minSdk` is 26 because of `scala-library`.** `d8` refuses to dex it below that — *"Increase the
  minSdkVersion to 26 or above"* — so an APK carrying the Scala runtime starts at Android 8.0. SDL's
  own floor is 21 and sysl's triple states 24; the three do not have to agree and the higher wins.

For comparison, Kotlin costs **nothing at all** under AGP 9 — it is compiled by AGP itself, and the
`org.jetbrains.kotlin.android` plugin is refused outright as no longer required. Scala is here
because it is what this project is written in everywhere else.

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

# androidkit

**A sysl program on Android, and the smallest one that shows anything.** Clone it, write over
`androidkit/main.sysl`, and you have an app. `sysl build-c` compiles the program to an archive, CMake
links that into the `.so` the APK carries, and `SDLActivity` loads it.

It puts one line of text on the screen. Everything else in the repository is the machinery that gets
it there, and every piece of it is commented with why — most of them are things this program got
wrong first.

```
sysl-lang/sdl3       windows, rendering, events and input
sysl-lang/sdl3-ttf   real text, rasterized from a font the device already has
```

The demo it used to carry — Box2D physics, a photograph tumbling among the shapes — moved to
`sysl-lang/bouncing` so that this one could stay small enough to read.

## Try it without building it

**[Download the APK](https://github.com/sysl-lang/androidkit/releases/latest/download/androidkit.apk)** and open it on an Android phone.

- **Android 8.0 or newer** (`minSdk 26`), and **arm64** — which is every phone since about 2015. It
  will *not* install on an x86_64 emulator, because sysl has one Android target and that is
  `aarch64-android`.
- Your phone will ask whether to allow installing from wherever you downloaded it. That prompt is
  what sideloading is; it is not a warning about this app in particular.
- It is signed with the project's own key rather than by a store, so it is not checked by anyone but
  you. The source is right here.

## Building it

You need Android Studio's SDK with the **NDK** and **CMake** installed (SDK Manager → SDK Tools),
**`sbt`** on the path, and `ANDROID_HOME` set:

```
export ANDROID_HOME=~/Library/Android/sdk
./fetch-sdl3.sh
./gradlew assembleDebug
```

`fetch-sdl3.sh` downloads SDL3's and SDL_ttf's official Android releases into `app/libs/`. Neither is
committed: they are tens of megabytes of binaries built against an NDK and an API level somebody else
chose, and what belongs in a repository is something a person can read. picokit makes the same call
about its pico-sdk clone.

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
| `androidkit/main.sysl` | the whole program — both exports, the physics, the drawing, and the image |
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
  `@export("Java_sh_sysl_androidkit_MainActivity_nativeSetSystemBars")` in `main.sysl` is. The insets
  it writes are module storage in that same file, which until sysl `e282d3cb` was impossible: an
  `@export` in a header-less file that reached module storage made every `@export` in the file vanish
  (card `0167`), so they lived in a module of their own purely to dodge it. The third
  option is `window.set_fullscreen(true)`, which hides the bars and hands the program the glass;
  that is what a game wants, and then every rectangle here is the whole window.
- **`android:theme` with `Theme.NoTitleBar.Fullscreen`.** An action bar over the top means SDL's
  surface starts below it, and everything drawn is offset by a bar height the program was never told
  about.
- **A time step in seconds, clamped.** The square moves at the same speed on a 60 Hz phone and a
  120 Hz one, and an app sent to the background does not come back with the square through a wall.

## Neither package knew it was on a phone

The demo is Box2D physics drawn by SDL3: a boxful of discs, squares and triangles with **no gravity**,
perfect restitution and no friction, so nothing ever settles. One body carries an image. Tap to throw
in another.

**Two of Box2D's defaults have to be turned off or it all stops**, and neither is a fact about physics:

- **`restitution_threshold`** — Box2D ignores restitution below a relative speed, one metre per second
  by default, because a stack that keeps bouncing a little is jitter. Here it means every glancing hit
  is inelastic and the box goes quiet within a minute. Zero says *always bounce*.
- **`contact_damping_ratio`** — the default of 10 is heavily damped, which is right for a pile that
  should settle and wrong for this.

**And the two settings are not enough on their own, because a rigid-body solver is not
energy-preserving and no tuning makes Box2D one.** A soft-step integrator applies restitution once
per contact with a bounded impulse, so it under-restores; and the friction below dissipates whenever
a contact slides. So the demo **measures the kinetic energy and puts it back**: `½mv² + ½Iω²` over
every loose body, and since scaling every velocity by `s` scales energy by `s²`, restoring a target
is one square root and one pass.

That is a governor rather than physics, and it is the honest way to have a demo that never winds
down — the alternative is pretending a number that keeps falling is conserved. The target rises
whenever it is exceeded, so a tap adds its body's energy to the budget rather than being scaled away.

**And a little friction, which is what lets the picture spin at all.** Every impulse on a
frictionless disc points along the line between the two centres, so it passes through the centre of
mass and its torque is exactly zero — a frictionless circle that starts at rest can never be made to
rotate, however hard it is hit. The thrown shapes spin only because they were given an
`angular_velocity`; the picture starts still by design. Box2D mixes the two shapes' friction as their
geometric mean, so one frictionless party makes the whole contact frictionless — which is why it is
on the walls and on everything thrown, not just on the picture.

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

## Text, and what it costs

**`sdl3-ttf` rasterizes a real outline font**, so the message is antialiased at whatever size it is
asked for. The font is one the device already has — `/system/fonts/Roboto-Regular.ttf` and friends,
tried in order — so nothing is packaged, nothing is looked up at run time, and the same source falls
back to a desktop font under `sysl run`.

**The string is turned into a texture once, not every frame.** Rasterizing glyphs and uploading a
texture sixty times a second to draw the same words is the mistake this binding makes easy.

**Where the compiled SDL_ttf comes from**: a prebuilt `libSDL3_ttf.so` inside the AAR, which Gradle's
prefab unpacks and packages into the APK. It is 20 MB in the archive and **1.9 MB in the APK** once
Gradle strips it — FreeType and HarfBuzz are linked into it statically, which is why it is not small.

**The cheaper option, if that matters**: SDL3 carries a fixed 8x8 bitmap font of its own, and
`renderer.debug_text(x, y, "…")` needs no second AAR, no font and no package at all. It is a
debugging font by SDL's own description — one size, one face, ASCII, no shaping — and scaling it up
looks exactly like scaling a bitmap up. This program used it first; the screenshots are the argument
for the 1.9 MB.

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

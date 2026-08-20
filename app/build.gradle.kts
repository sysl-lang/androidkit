plugins {
    id("com.android.application")
}

android {
    namespace = "sh.sysl.androidkit"
    compileSdk = 36

    // **Pinned, and pinned to the stable one.** AGP downloads whatever it defaults to if this is
    // absent, so leaving it out means the toolchain changes under you when AGP does. It also has to
    // be a version this machine will actually keep: `~/Library/Android/sdk/ndk/` here also holds an
    // `r30` beta, and `CMakeLists.txt` hands *this* NDK to `sysl build-c` precisely so the two halves
    // of the build cannot end up on different ones.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "sh.sysl.androidkit"

        // **21 is SDL's floor and it is the same number the sysl target carries.** The compiler's
        // triple is `aarch64-linux-android24`, which states 24 — so this is the one fact in the
        // project written down twice, and the higher of the two wins at the link. Raising `minSdk`
        // past 24 is free; lowering it below 24 produces an APK Android will install and a `.so`
        // built against declarations the device may not have.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_PLATFORM=android-24")
            }
        }

        // **One ABI, and it is not a limitation worth apologising for.** On an Apple Silicon host the
        // emulator runs `arm64-v8a`, and so does every Android device made since 2015 — so one build
        // covers the emulator and the hardware. `x86_64` matters only on an Intel host or a CI
        // runner, `armeabi-v7a` only for pre-2015 phones; adding either is a line here and a second
        // row in the sysl registry that does not exist yet.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    buildFeatures {
        // What unpacks the SDL3 AAR into the prefab layout that `find_package(SDL3 CONFIG)` reads.
        prefab = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // **There is no `java/` directory and that is deliberate.** AGP looks in `src/main/java` for
    // both languages by default, which would leave a Kotlin-only project with a directory named for
    // the language it does not use. Saying so costs one line.
    sourceSets["main"].java.srcDirs("src/main/kotlin")

    lint {
        abortOnError = false
    }
}

dependencies {
    // **The AAR is not in this repository** — `./fetch-sdl3.sh` downloads it, and `.gitignore` keeps
    // it out. It is 16 MB of binaries built against an NDK and an API level somebody else chose, and
    // the org's rule against carrying a prebuilt `.so` in a package is the same argument one level
    // up: what is committed here should be readable, and a `.so` is not. picokit makes the same
    // choice about its 81 MB pico-sdk clone.
    implementation(fileTree("libs") { include("*.aar") })
}

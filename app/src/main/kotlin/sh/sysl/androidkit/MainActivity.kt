package sh.sysl.androidkit

import org.libsdl.app.SDLActivity

/**
 * The whole Kotlin half of the program, and there is nothing in it.
 *
 * `SDLActivity` does everything: it creates the surface, pumps the looper, forwards touches and key
 * events, loads the native library and calls into it. What it needs from a project is a subclass to
 * name in the manifest — the launcher activity has to be a class in the application's own package,
 * and `SDLActivity` lives in the AAR — so this exists to have a name.
 *
 * **Two defaults are being taken rather than overridden.** `getMainSharedObject()` answers
 * `libmain.so` and `getMainFunction()` answers `SDL_main`, which is why `CMakeLists.txt` calls the
 * library `main` and why `main.sysl` exports that symbol. Both are overridable here and neither is
 * worth overriding: matching the defaults keeps the two halves agreeing with nothing configured
 * between them.
 *
 * Somewhere to start, if this file ever needs to do something: override `getArguments()` to pass a
 * command line through to `SDL_main`, or `getLibraries()` to load something beside SDL first.
 */
class MainActivity : SDLActivity()

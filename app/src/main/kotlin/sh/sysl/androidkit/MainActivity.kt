package sh.sysl.androidkit

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import org.libsdl.app.SDLActivity

/**
 * The Kotlin half of the program, which exists for two reasons.
 *
 * The first is that the manifest needs a launcher activity in the application's own package, and
 * `SDLActivity` lives in the AAR — so a subclass has to exist somewhere to have a name.
 *
 * **The second is the system bars, and it is the only code here.** `SDL_GetWindowSafeArea` answers
 * with Android's insets combined — `systemBars`, `systemGestures`, `mandatorySystemGestures`,
 * `tappableElement` and `displayCutout`, all at once — because it answers *where can a button go*.
 * For a drawing that is far too conservative: on a gesture-navigation phone the gesture strips take
 * 78 pixels off each side and the mandatory bottom gesture reaches above the navigation bar, none of
 * which is obscured or untouchable for something that is only being looked at.
 *
 * SDL exposes the combined rectangle and no way to ask for one kind, so a program that wants the
 * region *between the bars* has to read the insets on this side and hand them over. That is what
 * this does.
 *
 * **Two defaults are taken rather than overridden.** `getMainSharedObject()` answers `libmain.so`
 * and `getMainFunction()` answers `SDL_main`, which is why `CMakeLists.txt` calls the library `main`
 * and why `main.sysl` exports that symbol.
 */
class MainActivity : SDLActivity() {

    /**
     * Set in `main.sysl`, not here.
     *
     * JNI binds a native method by mangling the package and class into
     * `Java_sh_sysl_androidkit_MainActivity_nativeSetSystemBars`, and that string is what the sysl
     * side `@export`s. **Renaming this method, this class or this package renames the symbol** — the
     * link still succeeds, because JNI resolves at run time, and the failure is an
     * `UnsatisfiedLinkError` the first time the insets change.
     */
    private external fun nativeSetSystemBars(left: Int, top: Int, right: Int, bottom: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // **Listening on the content view rather than on SDL's surface**, which already has a
        // listener of its own — `SDLSurface` implements `OnApplyWindowInsetsListener` and is what
        // feeds SDL's own safe area. Taking that one over would break it.
        //
        // The insets are returned unconsumed, so everything below this in the hierarchy still sees
        // them.
        window.decorView.setOnApplyWindowInsetsListener { _: View, insets: WindowInsets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // **`systemBars()` alone** — the status bar and the navigation bar, and none of the
                // gesture regions. That is the whole difference between this and SDL's safe area.
                val bars = insets.getInsets(WindowInsets.Type.systemBars())

                nativeSetSystemBars(bars.left, bars.top, bars.right, bars.bottom)
            }

            insets
        }
    }
}

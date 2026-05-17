import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import korlibs.korge.view.Views
import korlibs.render.gameWindowAndroidContext

/** Android clipboard via the system [ClipboardManager]. */
actual fun Views.copyTextToClipboard(text: String) {
    val context = gameWindow.gameWindowAndroidContext as? Context ?: return
    // setPrimaryClip must run on a thread with a Looper; the game render thread has none.
    val copy = Runnable {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Trillium", text))
    }
    when (val activity = context as? Activity) {
        null -> copy.run()
        else -> activity.runOnUiThread(copy)
    }
}

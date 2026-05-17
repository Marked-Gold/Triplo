import korlibs.korge.view.Views
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Desktop/JVM clipboard via AWT. */
actual fun Views.copyTextToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

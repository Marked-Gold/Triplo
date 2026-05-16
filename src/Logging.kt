import korlibs.logger.Logger

/**
 * Lightweight logging shim.
 *
 * The project previously used the `napier` multiplatform logging library. KorGE 6 ships its own
 * multiplatform [Logger], so we keep the original `Napier.x(...)` call sites working by delegating
 * to it here instead of pulling in an extra dependency.
 */
object Napier {
    private val logger = Logger("Trillium")

    /** No-op kept for source compatibility with the old `Napier.base(DebugAntilog())` call. */
    fun base(antilog: Any? = null) {}

    fun v(message: String) = logger.trace { message }
    fun d(message: String) = logger.debug { message }
    fun i(message: String) = logger.info { message }
    fun w(message: String) = logger.warn { message }
    fun e(message: String) = logger.error { message }
}

/** No-op replacement for napier's `DebugAntilog`, kept so existing call sites still compile. */
class DebugAntilog

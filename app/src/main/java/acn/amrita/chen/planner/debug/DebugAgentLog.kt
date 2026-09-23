package acn.amrita.chen.planner.debug

/** Compatibility shim: private academic data must never be sent to debug collectors. */
object DebugAgentLog {
    @Suppress("UNUSED_PARAMETER")
    fun log(location: String, message: String, hypothesisId: String,
            data: Map<String, Any?> = emptyMap(), runId: String = "") = Unit
}

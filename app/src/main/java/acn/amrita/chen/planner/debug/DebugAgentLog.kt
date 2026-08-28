package acn.amrita.chen.planner.debug

object DebugAgentLog {
    fun log(
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        // #region agent log
        Thread {
            try {
                val payload = org.json.JSONObject()
                payload.put("sessionId", "4a562f")
                payload.put("location", location)
                payload.put("message", message)
                payload.put("timestamp", System.currentTimeMillis())
                payload.put("hypothesisId", hypothesisId)
                payload.put("runId", runId)
                val d = org.json.JSONObject()
                data.forEach { (k, v) -> d.put(k, v ?: org.json.JSONObject.NULL) }
                payload.put("data", d)
                val body = payload.toString().toByteArray()
                val urls = arrayOf(
                    "http://10.0.2.2:7755/ingest/635c21ac-5662-4399-b450-29a97c393225",
                    "http://127.0.0.1:7755/ingest/635c21ac-5662-4399-b450-29a97c393225"
                )
                for (u in urls) {
                    try {
                        val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("X-Debug-Session-Id", "4a562f")
                        conn.doOutput = true
                        conn.connectTimeout = 800
                        conn.readTimeout = 800
                        conn.outputStream.use { it.write(body) }
                        conn.inputStream?.close()
                        conn.disconnect()
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }.start()
        android.util.Log.d("DBG4a562f", "$hypothesisId $location $message $data")
        // #endregion
    }
}

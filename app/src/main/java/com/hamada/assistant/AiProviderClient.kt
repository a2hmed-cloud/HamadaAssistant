package com.hamada.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object AiProviderClient {
    private val executor = Executors.newSingleThreadExecutor()

    fun fetchModels(baseUrl: String, apiKey: String, callback: (Result<List<String>>) -> Unit) {
        executor.execute {
            try {
                require(baseUrl.startsWith("https://")) { "عنوان API لازم يبدأ بـ https://" }
                require(apiKey.isNotBlank()) { "أدخل API Key الأول" }
                val conn = (URL(baseUrl.trimEnd('/') + "/models").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    if (code !in 200..299) throw IllegalStateException("خطأ API $code: " + body.take(250))
                    val data = JSONObject(body).optJSONArray("data")
                        ?: throw IllegalStateException("المزوّد لم يرجع قائمة data متوافقة مع /models")
                    val models = (0 until data.length()).mapNotNull { i ->
                        data.optJSONObject(i)?.optString("id")?.trim()?.takeIf { it.isNotBlank() }
                    }.distinct().sorted()
                    if (models.isEmpty()) throw IllegalStateException("المزوّد رجّع قائمة موديلات فارغة")
                    callback(Result.success(models))
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}

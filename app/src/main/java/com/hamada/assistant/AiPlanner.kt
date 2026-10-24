package com.hamada.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class AgentAction(val tool: String, val target: String = "", val text: String = "", val reason: String = "")

class AiPlanner {
    private val executor = Executors.newSingleThreadExecutor()

    fun nextAction(
        baseUrl: String,
        apiKey: String,
        model: String,
        goal: String,
        screen: String,
        appPackage: String,
        history: List<String>,
        imageDataUrl: String = "",
        callback: (Result<AgentAction>) -> Unit
    ) {
        executor.execute {
            try {
                require(baseUrl.startsWith("https://")) { "عنوان API لازم يبدأ بـ https://" }
                require(apiKey.isNotBlank()) { "أدخل API Key من إعدادات عقل الذكاء الاصطناعي" }
                require(model.isNotBlank()) { "اكتب اسم الموديل" }
                val system = """أنت محرك تخطيط لمساعد أندرويد. اختر خطوة واحدة فقط في كل رد بناء على الهدف وملاحظة الشاشة.
أرجع JSON فقط بهذه المفاتيح: tool, target, text, reason.
الأدوات المسموحة: launch_app (target اسم التطبيق)، read_screen، click_text (target نص عنصر ظاهر)، enter_text (target اسم الحقل و text القيمة)، back، home، finish (text سبب الإنهاء).
لا تخترع عناصر غير ظاهرة. إذا كانت الشاشة لا تدعم الهدف، جرّب خطوة آمنة بديلة أو أنهِ المهمة مع توضيح أن النجاح غير مؤكد.
لا تنفذ شراء أو إرسال رسالة أو حذف أو مشاركة بيانات أو أي فعل حساس دون موافقة صريحة من المستخدم. لا تعتبر الضغط نجاحًا؛ افحص الملاحظة التالية. تجنب تكرار نفس الخطوة إذا فشلت. الحد الأقصى للخطوات يطبقه التطبيق."""
                val userText = JSONObject()
                    .put("goal", goal)
                    .put("current_app_package", appPackage)
                    .put("visible_screen_text", screen.take(7000))
                    .put("recent_steps", JSONArray(history.takeLast(8)))
                    .put("instruction", "أعد خطوة واحدة فقط كـ JSON. استخدم الصورة كدليل إضافي إذا كانت متاحة.")
                    .toString()
                val userContent = if (imageDataUrl.startsWith("data:image/")) {
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", userText))
                        .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageDataUrl)))
                } else userText
                val payload = JSONObject()
                    .put("model", model)
                    .put("temperature", 0.1)
                    .put("max_tokens", 300)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", userContent)))
                val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 25000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                try {
                    conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    if (code !in 200..299) throw IllegalStateException("خطأ API $code: " + body.take(350))
                    val content = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                    val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val json = JSONObject(clean)
                    val action = AgentAction(
                        tool = json.optString("tool", "finish").lowercase(),
                        target = json.optString("target"),
                        text = json.optString("text"),
                        reason = json.optString("reason")
                    )
                    val allowed = setOf("launch_app", "read_screen", "click_text", "enter_text", "back", "home", "finish")
                    require(action.tool in allowed) { "المحرك رجّع أداة غير مسموحة" }
                    callback(Result.success(action))
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}

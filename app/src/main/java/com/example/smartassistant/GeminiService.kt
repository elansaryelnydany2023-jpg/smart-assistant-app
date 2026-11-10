package com.example.smartassistant

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiService {
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    suspend fun generateReply(apiKey: String, userMessage: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "أدخل مفتاح Gemini API من شاشة الإعدادات لتفعيل الردود الذكية."
        }

        try {
            val connection = URL("$API_URL?key=$apiKey").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val body = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", userMessage))
                        )
                    )
                )
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val candidates = JSONObject(response).optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            parts?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                ?: "لم أتمكن من الحصول على رد من Gemini."
        } catch (e: Exception) {
            Log.e("GeminiService", "Gemini request failed", e)
            "حدث خطأ أثناء الاتصال بـ Gemini. تأكد من صحة المفتاح والاتصال بالإنترنت."
        }
    }
}

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
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    suspend fun generateReply(apiKey: String, userMessage: String): String = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            return@withContext "أدخل مفتاح Gemini API من شاشة الإعدادات لتفعيل الردود الذكية."
        }

        try {
            val connection = URL("$API_URL?key=$key").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
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

            val code = connection.responseCode
            val response = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code !in 200..299) {
                Log.e("GeminiService", "HTTP $code: $response")
                return@withContext when (code) {
                    400 -> "المفتاح أو الطلب غير صحيح. تأكد من نسخ مفتاح Gemini بدون مسافات."
                    401, 403 -> "مفتاح Gemini غير صالح أو غير مصرح له. أنشئ مفتاحًا جديدًا من Google AI Studio."
                    429 -> "تم تجاوز حد الاستخدام مؤقتًا. انتظر قليلًا ثم حاول مرة أخرى."
                    else -> "فشل الاتصال بـ Gemini (رمز $code). تأكد من الإنترنت وحاول مرة أخرى."
                }
            }

            val candidates = JSONObject(response).optJSONArray("candidates")
            val content = candidates?.optJSONObject(0)?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            parts?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                ?: "لم يرجع Gemini ردًا واضحًا. حاول مرة أخرى."
        } catch (e: Exception) {
            Log.e("GeminiService", "Gemini request failed", e)
            "تعذر الاتصال بالإنترنت أو بخدمة Gemini. تأكد من اتصال الهاتف ثم حاول مرة أخرى."
        }
    }
}

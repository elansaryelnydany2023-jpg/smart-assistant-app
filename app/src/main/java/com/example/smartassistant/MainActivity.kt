package com.example.smartassistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AssistantScreen()
                }
            }
        }
    }
}

@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("smart_assistant", Context.MODE_PRIVATE)
    val messages = remember { mutableStateListOf("المساعد: مرحبًا! كيف أساعدك اليوم؟") }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showSettings) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "إعدادات Gemini", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                label = { Text("مفتاح Gemini API") }
            )
            Button(
                onClick = {
                    prefs.edit().putString("gemini_api_key", apiKey).apply()
                    showSettings = false
                    messages.add("المساعد: تم حفظ المفتاح. يمكنك الآن استخدام الردود الذكية.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ المفتاح")
            }
            TextButton(
                onClick = { showSettings = false },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("رجوع")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "المساعد الذكي", style = MaterialTheme.typography.headlineSmall)

        OutlinedButton(
            onClick = { showSettings = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Text("⚙️ الإعدادات وإدخال مفتاح Gemini")
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            items(messages) { message ->
                Text(text = message, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("اكتب رسالتك") },
                enabled = !isLoading
            )
            Button(
                onClick = {
                    val message = input.trim()
                    if (message.isNotBlank() && !isLoading) {
                        messages.add("أنت: $message")
                        input = ""
                        isLoading = true
                        scope.launch {
                            val reply = GeminiService.generateReply(apiKey, message)
                            messages.add("المساعد: $reply")
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "..." else "إرسال")
            }
        }
    }
}

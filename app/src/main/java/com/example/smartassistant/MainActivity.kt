package com.example.smartassistant

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val messages = remember { mutableStateListOf("المساعد: مرحبًا! كيف أساعدك اليوم؟") }
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "المساعد الذكي", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            items(messages) { message ->
                Text(text = message, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("اكتب رسالتك") }
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add("أنت: $input")
                        messages.add("المساعد: ${generateReply(input)}")
                        input = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("إرسال")
            }
        }
    }
}

fun generateReply(message: String): String {
    val text = message.trim().lowercase()
    return when {
        text.contains("مرحبا") || text.contains("السلام") -> "أهلًا بك! كيف يمكنني مساعدتك؟"
        text.contains("كيف حالك") -> "أنا بخير، شكرًا لسؤالك."
        text.contains("وقت") -> "لا أستطيع معرفة الوقت الحالي داخل هذه النسخة التجريبية."
        text.contains("شكرا") -> "على الرحب والسعة!"
        else -> "استلمت رسالتك. هذه نسخة تجريبية من المساعد الذكي."
    }
}

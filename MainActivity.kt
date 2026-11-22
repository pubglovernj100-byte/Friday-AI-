package com.friday.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

private val Bg = Color(0xFF070B14)
private val Panel = Color(0xFF10182A)
private val Cyan = Color(0xFF48D8FF)
private val TextMain = Color(0xFFE8F1FF)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { FridayScreen(this, { say(it) }) }
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN") }
    private fun say(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "friday-reply") }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

data class ChatMessage(val text: String, val fromUser: Boolean)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridayScreen(activity: MainActivity, speak: (String) -> Unit) {
    val prefs = activity.getSharedPreferences("friday", android.content.Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("ONLINE • READY") }
    var chat by remember { mutableStateOf(loadChat(prefs)) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) input = spoken
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech(speechLauncher) else status = "MIC PERMISSION DENIED"
    }
    fun saveChat() {
        val arr = JSONArray()
        chat.takeLast(30).forEach { arr.put(JSONObject().put("text", it.text).put("user", it.fromUser)) }
        prefs.edit().putString("chat", arr.toString()).apply()
    }
    fun send() {
        val prompt = input.trim()
        if (prompt.isBlank() || busy) return
        // Local memory commands: "yaad rakho ..." or "remember ..." save on-device.
        val lowerPrompt = prompt.lowercase(Locale.ROOT)
        if (lowerPrompt.startsWith("yaad rakho ") || lowerPrompt.startsWith("remember ") || lowerPrompt.startsWith("remember that ")) {
            val memory = prompt.substringAfter(" ").removePrefix("that ").trim()
            val existing = prefs.getString("memories", "") ?: ""
            val updated = (existing.lines().filter { it.isNotBlank() } + memory).distinct().takeLast(50).joinToString("\n")
            prefs.edit().putString("memories", updated).apply()
            input = ""
            chat = chat + ChatMessage(prompt, true) + ChatMessage("Yaad rakh liya: $memory", false)
            saveChat(); speak("Yaad rakh liya."); return
        }
        if (lowerPrompt == "meri memory dikhao" || lowerPrompt == "show my memory" || lowerPrompt == "what do you remember") {
            val memories = prefs.getString("memories", "") ?: ""
            val reply = if (memories.isBlank()) "Abhi meri memory mein kuch save nahi hai. 'Yaad rakho ...' bolkar koi baat save kar sakte ho." else "Meri saved memory:\n" + memories
            input = ""; chat = chat + ChatMessage(prompt, true) + ChatMessage(reply, false); saveChat(); speak(reply); return
        }
        if (lowerPrompt == "memory clear" || lowerPrompt == "meri memory delete karo") {
            prefs.edit().remove("memories").apply()
            input = ""; val reply = "Local memory clear kar di."; chat = chat + ChatMessage(prompt, true) + ChatMessage(reply, false); saveChat(); speak(reply); return
        }
        input = ""
        chat = chat + ChatMessage(prompt, true)
        saveChat()
        val phoneHandled = handlePhoneCommand(activity, prompt)
        if (phoneHandled != null) {
            chat = chat + ChatMessage(phoneHandled, false); saveChat(); speak(phoneHandled); return
        }
        if (apiKey.isBlank()) {
            val msg = "Mujhe activate karne ke liye Settings mein OpenAI API key add karo. API key ke bina mera AI brain connect nahi hoga."
            chat = chat + ChatMessage(msg, false); saveChat(); speak(msg); return
        }
        busy = true; status = "THINKING…"
        scope.launch {
            val memories = prefs.getString("memories", "") ?: ""
            val answer = withContext(Dispatchers.IO) { callAI(apiKey, prompt, chat.takeLast(12), memories) }
            chat = chat + ChatMessage(answer, false); saveChat()
            busy = false; status = "ONLINE • READY"; speak(answer)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FRIDAY", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text(status, color = Color(0xFF91A8C7), fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = Cyan) }
            IconButton(onClick = { chat = listOf(ChatMessage("Hi! Main FRIDAY hoon. Mujhse Hindi ya Hinglish mein baat karo.", false)); saveChat() }) {
                Icon(Icons.Default.Delete, "Clear chat", tint = Color(0xFF90A2BE))
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(104.dp).background(Color(0xFF11273A), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(78.dp).background(Color(0xFF123F58), CircleShape), contentAlignment = Alignment.Center) {
                    Text("F", color = Cyan, fontSize = 40.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), reverseLayout = false, contentPadding = PaddingValues(vertical = 10.dp)) {
            items(chat) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.widthIn(max = 310.dp).padding(vertical = 5.dp).background(if (m.fromUser) Color(0xFF12324A) else Panel, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Text(if (m.fromUser) "YOU" else "FRIDAY", color = if (m.fromUser) Color(0xFF9FC5E7) else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp)); Text(m.text, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
            if (busy) item { Text("FRIDAY is thinking…", color = Cyan, modifier = Modifier.padding(10.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp).background(Panel, RoundedCornerShape(24.dp)).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchSpeech(speechLauncher) else audioPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                Icon(Icons.Default.Mic, "Speak", tint = Cyan)
            }
            TextField(input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("FRIDAY ko message karo…", color = Color.Gray) }, colors = TextFieldDefaults.colors(focusedContainerColor = Panel, unfocusedContainerColor = Panel, focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = Cyan, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }), maxLines = 4)
            IconButton(onClick = { send() }) { Icon(Icons.Default.Send, "Send", tint = Cyan) }
        }
    }
    if (showSettings) {
        AlertDialog(onDismissRequest = { showSettings = false }, containerColor = Panel, title = { Text("FRIDAY Setup", color = Cyan) }, text = {
            Column {
                Text("OpenAI API key (personal use)", color = TextMain)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(apiKey, onValueChange = { apiKey = it }, label = { Text("API key") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                Text("Key is stored on this device. Never share your APK with this key included.", color = Color(0xFF9AAAC0), fontSize = 12.sp)
            }
        }, confirmButton = { TextButton(onClick = { prefs.edit().putString("api_key", apiKey.trim()).apply(); showSettings = false }) { Text("SAVE", color = Cyan) } }, dismissButton = { TextButton(onClick = { showSettings = false }) { Text("CANCEL", color = TextMain) } })
    }
}

private fun launchSpeech(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "FRIDAY ko bolo…")
    }
    try { launcher.launch(intent) } catch (_: Exception) { }
}


private fun loadChat(prefs: android.content.SharedPreferences): List<ChatMessage> {
    val raw = prefs.getString("chat", null) ?: return listOf(ChatMessage("Hi! Main FRIDAY hoon. Mujhse Hindi ya Hinglish mein baat karo.", false))
    return try { val a = JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it).let { ChatMessage(it.getString("text"), it.getBoolean("user")) } } } catch (_: Exception) { listOf(ChatMessage("Hi! Main FRIDAY hoon.", false)) }
}


private fun handlePhoneCommand(activity: MainActivity, text: String): String? {
    val t = text.lowercase(Locale.ROOT)
    return try {
        when {
            "camera" in t || "कैमरा" in t || "camera kholo" in t -> { activity.startActivity(Intent("android.media.action.IMAGE_CAPTURE")); "Camera khol diya." }
            "dialer" in t || "phone kholo" in t || "call screen" in t || "डायलर" in t -> { activity.startActivity(Intent(Intent.ACTION_DIAL)); "Dialer khol diya. Number confirm karke call karna." }
            "settings" in t || "सेटिंग" in t -> { activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)); "Phone settings khol raha hoon." }
            "youtube" in t || "यूट्यूब" in t -> { activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))); "YouTube khol raha hoon." }
            else -> null
        }
    } catch (_: Exception) { "Ye command is phone par open nahi ho paayi." }
}


private suspend fun callAI(key: String, prompt: String, history: List<ChatMessage>, memories: String): String {
    return try {
        val messages = JSONArray()
        val systemPrompt = "You are FRIDAY, a friendly, capable personal AI assistant. Respond in Hindi/Hinglish matching the user. Keep answers concise but helpful. You cannot claim to execute phone actions unless the app did them." + if (memories.isNotBlank()) "\n\nUser-approved local memories (use only when relevant):\n$memories" else ""
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.takeLast(12).forEach { messages.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
        // Last user prompt is already present in history; avoid sending it twice.
        val body = JSONObject().put("model", "gpt-4o-mini").put("messages", messages).put("temperature", 0.7)
        val request = Request.Builder().url("https://api.openai.com/v1/chat/completions").addHeader("Authorization", "Bearer $key").addHeader("Content-Type", "application/json").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val response = OkHttpClient().newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) "AI API error (${response.code}): ${JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "Check API key and billing."}"
        else JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    } catch (e: Exception) { "Connection error: ${e.localizedMessage ?: "network unavailable"}. Internet aur API key check karo." }
}

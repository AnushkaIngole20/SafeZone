package com.example.safezone

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat as FirebaseChat
import com.google.firebase.ai.GenerativeModel as FirebaseGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content as firebaseContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SafetyAssistantService(private val context: Context? = null) {

    interface AssistantCallback {
        fun onResponse(reply: String, isGenerativeAi: Boolean)
        fun onError(errorMessage: String)
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    // Chat history for multi-turn Gemini REST conversations
    private val chatHistory = mutableListOf<JSONObject>()

    // Firebase AI Logic Client (as secondary fallback if configured)
    private var firebaseGenerativeModel: FirebaseGenerativeModel? = null
    private var firebaseChatSession: FirebaseChat? = null

    init {
        initFirebaseAi()
    }

    fun getActiveApiKey(): String {
        val prefs = context?.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val userKey = prefs?.getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
        if (userKey.isNotEmpty()) {
            return userKey
        }
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun hasValidApiKey(): Boolean {
        val key = getActiveApiKey()
        return key.isNotEmpty() && !key.equals("null", ignoreCase = true)
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        context?.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_GEMINI_API_KEY, trimmed)
            ?.apply()
        resetSession()
    }

    fun resetSession() {
        synchronized(chatHistory) {
            chatHistory.clear()
        }
        try {
            firebaseChatSession = firebaseGenerativeModel?.startChat()
        } catch (_: Throwable) {
            firebaseChatSession = null
        }
    }

    private fun initFirebaseAi() {
        try {
            val systemPrompt = firebaseContent { text(SYSTEM_PROMPT) }
            firebaseGenerativeModel = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                systemInstruction = systemPrompt
            )
            firebaseChatSession = firebaseGenerativeModel?.startChat()
        } catch (_: Throwable) {
            firebaseGenerativeModel = null
            firebaseChatSession = null
        }
    }

    /**
     * Sends user prompt to Gemini AI.
     * 1. Uses Gemini REST API with provided key (gemini-3.5-flash-lite / flash-lite-latest / 3.5-flash).
     * 2. Falls back to Firebase AI Logic if available.
     * 3. Falls back to local rule-based safety engine.
     */
    fun generateResponse(userPrompt: String, callback: AssistantCallback) {
        val trimmedPrompt = userPrompt.trim()
        if (trimmedPrompt.isEmpty()) {
            callback.onError("Message cannot be empty.")
            return
        }

        scope.launch {
            val activeKey = getActiveApiKey()

            // Path A: Google AI Studio REST API using API Key
            if (activeKey.isNotEmpty() && !activeKey.equals("null", ignoreCase = true)) {
                try {
                    val restReply = withContext(Dispatchers.IO) {
                        callGeminiRestApi(activeKey, trimmedPrompt)
                    }
                    if (!restReply.isNullOrBlank()) {
                        callback.onResponse(restReply, true)
                        return@launch
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Gemini REST execution failed: ${t.message}")
                }
            }

            // Path B: Firebase AI Logic
            if (firebaseChatSession != null) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        firebaseChatSession!!.sendMessage(trimmedPrompt)
                    }
                    val reply = response.text?.trim()
                    if (!reply.isNullOrEmpty()) {
                        callback.onResponse(reply, true)
                        return@launch
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Firebase AI failed: ${t.message}")
                }
            }

            // Path C: Offline Rule-Based Safety Engine Fallback
            val fallback = getRuleBasedSafetyResponse(trimmedPrompt)
            callback.onResponse(fallback, false)
        }
    }

    private fun callGeminiRestApi(apiKey: String, prompt: String): String? {
        val candidateModels = listOf(
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-3.5-flash",
            "gemini-3.8-flash"
        )

        for (model in candidateModels) {
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val url = URL(endpoint)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 12000
                conn.readTimeout = 18000
                conn.doOutput = true

                // Build request payload
                val payload = JSONObject()

                val sysInstruction = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                    }
                    put("parts", partsArr)
                }
                payload.put("systemInstruction", sysInstruction)

                val contentsArr = JSONArray()
                synchronized(chatHistory) {
                    for (historyItem in chatHistory) {
                        contentsArr.put(historyItem)
                    }
                }

                val currentMsg = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                }
                contentsArr.put(currentMsg)
                payload.put("contents", contentsArr)

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                    val jsonResponse = JSONObject(responseStr)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val replyText = parts.getJSONObject(0).optString("text", "").trim()
                            if (replyText.isNotEmpty()) {
                                synchronized(chatHistory) {
                                    chatHistory.add(currentMsg)
                                    val modelMsg = JSONObject().apply {
                                        put("role", "model")
                                        put("parts", JSONArray().apply {
                                            put(JSONObject().apply { put("text", replyText) })
                                        })
                                    }
                                    chatHistory.add(modelMsg)
                                    while (chatHistory.size > 12) {
                                        chatHistory.removeAt(0)
                                    }
                                }
                                return replyText
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Model $model returned HTTP $responseCode")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error with model $model: ${t.message}")
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
        return null
    }

    companion object {
        private const val TAG = "SafetyAssistantService"
        const val PREF_SETTINGS = "SafeZoneSettings"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"

        const val SYSTEM_PROMPT = """
            You are SAFEZONE AI, an expert emergency and personal safety assistant.
            Your critical instructions:
            1. In immediate danger, medical, or fire emergencies, provide concise, calm, and actionable life-saving instructions first.
            2. Urge the user to dial local emergency numbers (such as 112, 100 for Police, 101 for Fire, 108 for Ambulance in India) and use SAFEZONE SOS when in physical peril.
            3. Keep crisis answers clear and readable—seconds count during an emergency.
            4. Provide practical safety steps for theft, harassment, natural disasters, first aid, and travel safety.
            5. Maintain a calm, protective, and reassuring safety-first tone.
        """

        fun getRuleBasedSafetyResponse(message: String): String {
            val text = message.lowercase()

            if (text.contains("emergency") || text.contains("sos")) {
                return "🚨 EMERGENCY ALERT:\n\n" +
                        "1. If in physical danger, move to safety immediately.\n" +
                        "2. Tap the red SEND SOS button on the dashboard to dispatch your GPS location to your trusted contacts.\n" +
                        "3. Call National Emergency Number 112 (India) or your local emergency dispatch."
            }

            if (text.contains("fire")) {
                return "🔥 FIRE EMERGENCY PROTOCOL:\n\n" +
                        "• Alert others and pull the nearest alarm.\n" +
                        "• Crawl low under smoke; toxic gases rise.\n" +
                        "• DO NOT use elevators under any circumstances.\n" +
                        "• Check doors for heat with the back of your hand before opening.\n" +
                        "• Dial Fire Services at 101 once outside."
            }

            if (text.contains("medical") || text.contains("bleed") || text.contains("unconscious") || text.contains("cpr")) {
                return "🏥 MEDICAL EMERGENCY GUIDANCE:\n\n" +
                        "• Call 108 or 112 immediately for an ambulance.\n" +
                        "• For bleeding: Apply firm, direct pressure with a clean cloth.\n" +
                        "• If unresponsive and not breathing: Begin chest compressions at 100-120 bpm.\n" +
                        "• Keep the person still and warm until medical help arrives."
            }

            if (text.contains("police") || text.contains("theft") || text.contains("stalk") || text.contains("threat")) {
                return "👮 POLICE & PERSONAL THREAT ACTION:\n\n" +
                        "• Move into a well-lit, public area with other people immediately.\n" +
                        "• Call 100 or 112 to request police dispatch.\n" +
                        "• Share your live location via SAFEZONE with your trusted emergency contacts.\n" +
                        "• Note descriptions of persons or vehicles if safe to do so."
            }

            return "🛡️ SAFEZONE Safety Assistant:\n\n" +
                    "I am ready to help you with emergency response, first aid, fire safety, and personal protection.\n\n" +
                    "• In danger? Use the SOS button immediately.\n" +
                    "• You can ask me any safety question or customize your emergency settings."
        }
    }
}

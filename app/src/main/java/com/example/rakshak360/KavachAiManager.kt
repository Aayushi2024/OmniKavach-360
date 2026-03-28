package com.example.rakshak360

import android.content.Context
import android.util.Log
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import com.runanywhere.sdk.public.extensions.*
import com.runanywhere.sdk.public.extensions.Models.ModelCategory
import com.runanywhere.sdk.storage.AndroidPlatformContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch

class KavachAiManager(private val context: Context) {

    private var isReady = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    interface OnReadyCallback {
        fun onReady()
        fun onError(error: String)
    }

    interface OnProgressCallback {
        fun onProgress(percent: Int)
        fun onDone()
        fun onError(error: String)
    }

    interface OnResponseCallback {
        fun onToken(token: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }

    companion object {
        private const val TAG = "KavachAiManager"
        private const val MODEL_ID = "smollm2-360m-instruct-q8_0"
        private const val SYSTEM_PROMPT =
            "You are Kavach AI, a safety assistant for Indian users. " +
                    "Always reply in Hinglish. Be helpful and concise. " +
                    "For emergencies: Police 100, Ambulance 108, " +
                    "Cyber Crime 1930, Women helpline 1091. " +
                    "OTP/bank/lottery calls = always scam, warn clearly."
    }

    fun initialize(progressCallback: OnProgressCallback, readyCallback: OnReadyCallback) {
        scope.launch {
            try {
                AndroidPlatformContext.initialize(context)
                RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)

                val runanywherePath = context.filesDir
                    .resolve("runanywhere").also { it.mkdirs() }
                CppBridgeModelPaths.setBaseDirectory(runanywherePath.absolutePath)

                try { LlamaCPP.register(priority = 100) }
                catch (e: Exception) { Log.w(TAG, "LlamaCPP: ${e.message}") }
                try { ONNX.register(priority = 100) }
                catch (e: Exception) { Log.w(TAG, "ONNX: ${e.message}") }

                RunAnywhere.registerModel(
                    id = MODEL_ID,
                    name = "SmolLM2 360M Kavach",
                    url = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q8_0.gguf",
                    framework = InferenceFramework.LLAMA_CPP,
                    modality = ModelCategory.LANGUAGE,
                    memoryRequirement = 400_000_000L
                )

                withContext(Dispatchers.Main) { progressCallback.onProgress(0) }

                RunAnywhere.downloadModel(MODEL_ID)
                    .catch { e ->
                        withContext(Dispatchers.Main) {
                            progressCallback.onError("Download failed: ${e.message}")
                        }
                    }
                    .collect { progress ->
                        val percent = (progress.progress * 100).toInt()
                        withContext(Dispatchers.Main) { progressCallback.onProgress(percent) }
                    }

                RunAnywhere.loadLLMModel(MODEL_ID)
                isReady = true

                withContext(Dispatchers.Main) {
                    progressCallback.onDone()
                    readyCallback.onReady()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Init failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    readyCallback.onError(e.message ?: "Init error")
                    progressCallback.onError(e.message ?: "Init error")
                }
            }
        }
    }

    fun generateResponse(userMessage: String, callback: OnResponseCallback) {
        if (!isReady) {
            callback.onError("Model abhi load nahi hua")
            return
        }
        scope.launch {
            try {
                val response = RunAnywhere.chat(
                    "$SYSTEM_PROMPT\n\nUser: $userMessage\nAssistant:"
                )
                withContext(Dispatchers.Main) {
                    callback.onToken(response)
                    callback.onComplete(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "Generation error")
                }
            }
        }
    }

    fun isModelReady(): Boolean = isReady

    fun destroy() {
        scope.cancel()
        isReady = false
    }
}
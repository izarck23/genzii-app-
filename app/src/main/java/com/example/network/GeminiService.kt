package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.model.ChatMessage
import com.example.model.OriginalityReport
import com.example.model.SourceMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiService {
  private const val TAG = "GeminiService"
  private const val MODEL_NAME = "gemini-2.5-flash"
  private const val FALLBACK_MODEL = "gemini-flash-latest"
  private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
  private const val STREAM_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:streamGenerateContent?alt=sse"
  private const val FALLBACK_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$FALLBACK_MODEL:generateContent"
  private const val FALLBACK_STREAM_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$FALLBACK_MODEL:streamGenerateContent?alt=sse"

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  /**
   * Validates whether a provided API key string is non-empty, non-placeholder, and of sufficient length.
   */
  fun isKeyValid(key: String?): Boolean {
    val clean = key?.trim() ?: return false
    return clean.length > 10 && clean != "MY_GEMINI_API_KEY" && !clean.contains("PLACEHOLDER", ignoreCase = true)
  }

  /**
   * Resolves the active API key, prioritizing valid custom user keys over injected secrets.
   */
  fun getActiveApiKey(customKey: String? = null): String {
    val cleanCustom = customKey?.trim()
    if (isKeyValid(cleanCustom)) {
      return cleanCustom!!
    }
    return try {
      val key = BuildConfig.GEMINI_API_KEY
      if (isKeyValid(key)) {
        key
      } else {
        ""
      }
    } catch (_: Throwable) {
      ""
    }
  }

  /**
   * Checks if a valid non-placeholder Gemini API key is configured.
   */
  fun isApiKeyConfigured(customKey: String? = null): Boolean {
    return getActiveApiKey(customKey).isNotBlank()
  }

  /**
   * Calls Google Gemini 3.5 Flash to analyze text for academic originality,
   * similarity metrics, potential public sources, and style integrity.
   */
  suspend fun analyzeOriginality(
    documentTitle: String,
    textToAnalyze: String,
    customApiKey: String? = null
  ): Result<OriginalityReport> = withContext(Dispatchers.IO) {
    val apiKey = getActiveApiKey(customApiKey)

    if (!isApiKeyConfigured(customApiKey)) {
      return@withContext Result.failure(
        IllegalStateException("Google Gemini API key is not configured. Please configure your key in Settings or AI Studio Secrets.")
      )
    }

    val wordCount = textToAnalyze.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    val prompt = """
      You are an expert academic originality analysis and plagiarism verification engine.
      Analyze the following document for authenticity, similarity against published literature, and originality metrics.
      
      Document Title: "$documentTitle"
      Text to analyze:
      \"\"\"
      $textToAnalyze
      \"\"\"
      
      STRICT REQUIREMENTS:
      - Never invent matching websites, fake URLs, or fabricate plagiarism percentages without evidence.
      - If the text is genuine, original writing with no detected public plagiarism, report originalPercentage: 100, similarityPercentage: 0, matchesCount: 0, sourcesCount: 0, and an empty sources array [].
      - Only list verifiable academic sources or public domains if genuine conceptual or verbatim similarity is identified.
      - Provide an honest, insightful "aiAnalysis" evaluating the syntactic patterns, vocabulary sophistication, coherence, and originality profile.
      - In "highlightedMatches", provide the text with bracketed annotations for any identified sections, or a clear excerpt summary if fully original.
      
      Respond strictly with a JSON object in this format:
      {
        "originalPercentage": <integer between 0 and 100>,
        "similarityPercentage": <integer between 0 and 100, such that originalPercentage + similarityPercentage == 100>,
        "matchesCount": <integer count of matching segments, 0 if original>,
        "sourcesCount": <integer count of identified sources, 0 if none>,
        "sources": [
          {
            "domain": "<source domain, e.g. arxiv.org or empty string if none>",
            "url": "<source URL or empty string>",
            "matchPercentage": <integer percentage>,
            "excerpt": "<excerpt from matching text or literature context>"
          }
        ],
        "aiAnalysis": "<honest 2-3 sentence AI evaluation of originality and writing authenticity>",
        "highlightedMatches": "<annotated excerpt or originality confirmation>"
      }
    """.trimIndent()

    try {
      val requestBodyJson = JSONObject().apply {
        val contentsArray = JSONArray().apply {
          val contentObj = JSONObject().apply {
            val partsArray = JSONArray().apply {
              put(JSONObject().put("text", prompt))
            }
            put("parts", partsArray)
          }
          put(contentObj)
        }
        put("contents", contentsArray)

        val generationConfig = JSONObject().apply {
          put("temperature", 0.2)
          put("responseMimeType", "application/json")
        }
        put("generationConfig", generationConfig)
      }

      var url = "$BASE_URL?key=$apiKey"
      var request = Request.Builder()
        .url(url)
        .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
        .build()

      var response = okHttpClient.newCall(request).execute()
      var responseString = response.body?.string() ?: ""

      if (!response.isSuccessful && (response.code == 404 || responseString.contains("no longer available", ignoreCase = true))) {
        Log.w(TAG, "Primary model returned ${response.code}, retrying with fallback model: $FALLBACK_MODEL")
        url = "$FALLBACK_BASE_URL?key=$apiKey"
        request = Request.Builder()
          .url(url)
          .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
          .build()
        response = okHttpClient.newCall(request).execute()
        responseString = response.body?.string() ?: ""
      }

      if (!response.isSuccessful) {
        val errorMsg = when (response.code) {
          429 -> "Rate limit reached (HTTP 429). Please wait a moment and try again."
          400, 403 -> "Invalid or unauthorized Gemini API key. Please check your key in Settings."
          else -> "Gemini API call failed with HTTP ${response.code}."
        }
        Log.e(TAG, "Gemini API error code: ${response.code}, body: $responseString")
        return@withContext Result.failure(Exception(errorMsg))
      }

      val responseJson = JSONObject(responseString)
      val candidates = responseJson.optJSONArray("candidates")
      val firstCandidate = candidates?.optJSONObject(0)
      val content = firstCandidate?.optJSONObject("content")
      val parts = content?.optJSONArray("parts")
      val firstPart = parts?.optJSONObject(0)
      val rawText = firstPart?.optString("text")?.trim() ?: "{}"

      val cleanJson = if (rawText.startsWith("```")) {
        rawText.substringAfter("\n").substringBeforeLast("```").trim()
      } else rawText

      val analysisJson = JSONObject(cleanJson)
      val origPct = if (analysisJson.has("originalPercentage")) {
        analysisJson.getInt("originalPercentage").coerceIn(0, 100)
      } else 100
      val simPct = if (analysisJson.has("similarityPercentage")) {
        analysisJson.getInt("similarityPercentage").coerceIn(0, 100)
      } else (100 - origPct).coerceIn(0, 100)
      val matches = analysisJson.optInt("matchesCount", 0).coerceAtLeast(0)
      val sourcesCount = analysisJson.optInt("sourcesCount", 0).coerceAtLeast(0)

      val sourcesList = mutableListOf<SourceMatch>()
      val sourcesArray = analysisJson.optJSONArray("sources")
      if (sourcesArray != null) {
        for (i in 0 until sourcesArray.length()) {
          val srcObj = sourcesArray.optJSONObject(i) ?: continue
          val domain = srcObj.optString("domain", "").trim()
          val url = srcObj.optString("url", "").trim()
          val matchPercentage = srcObj.optInt("matchPercentage", 0).coerceIn(0, 100)
          val excerpt = srcObj.optString("excerpt", "").trim()
          if (domain.isNotBlank() || excerpt.isNotBlank()) {
            sourcesList.add(
              SourceMatch(
                domain = domain.ifBlank { "Academic Literature Index" },
                url = url,
                matchPercentage = matchPercentage,
                excerpt = excerpt
              )
            )
          }
        }
      }

      val aiAnalysis = analysisJson.optString(
        "aiAnalysis",
        analysisJson.optString("summary", "Originality analysis verified by Google Gemini.")
      )
      val highlightedContent = analysisJson.optString(
        "highlightedMatches",
        aiAnalysis.ifBlank { textToAnalyze }
      )

      val report = OriginalityReport(
        id = "gemini-${UUID.randomUUID().toString().take(8)}",
        documentTitle = documentTitle,
        originalPercentage = origPct,
        similarityPercentage = simPct,
        matchesCount = matches,
        sourcesCount = sourcesList.size.coerceAtLeast(sourcesCount),
        wordsCount = wordCount.coerceAtLeast(1),
        scanDate = "Today, Just now (Gemini AI)",
        sources = sourcesList,
        highlightedContent = highlightedContent,
        aiAnalysis = aiAnalysis
      )

      Result.success(report)
    } catch (e: Exception) {
      Log.e(TAG, "Error in Gemini originality check", e)
      Result.failure(e)
    }
  }

  /**
   * Real-time ChatGPT-style Server-Sent Events (SSE) streaming chat with Google Gemini 2.5 Flash.
   * Supports multi-turn conversation history, contextual file analysis, and honest error reporting.
   */
  suspend fun streamChatWithGemini(
    userMessage: String,
    conversationHistory: List<ChatMessage> = emptyList(),
    fileContext: String? = null,
    fileName: String? = null,
    customApiKey: String? = null,
    temperature: Float = 0.5f,
    onChunkReceived: (String) -> Unit
  ): Result<String> = withContext(Dispatchers.IO) {
    val apiKey = getActiveApiKey(customApiKey)

    if (!isApiKeyConfigured(customApiKey)) {
      return@withContext Result.failure(
        IllegalStateException("Gemini API key is not configured. Please enter your API key in AI Studio Secrets panel or Settings.")
      )
    }

    val systemPrompt = """
      You are Genzii, an elite academic AI assistant, researcher, and scholarly writing partner.
      Provide comprehensive, clear, structured responses using Markdown formatting.
      Use headings, bold key concepts, and actionable bullet points.
      When contextual document information is supplied, reference specific findings directly from the document.
    """.trimIndent()

    try {
      val requestBodyJson = JSONObject().apply {
        val contentsArray = JSONArray()

        // Include recent conversation context (up to last 6 turns)
        val recentHistory = conversationHistory.takeLast(6)
        for (item in recentHistory) {
          val role = if (item.isUser) "user" else "model"
          contentsArray.put(
            JSONObject().apply {
              put("role", role)
              put("parts", JSONArray().apply {
                put(JSONObject().put("text", item.text))
              })
            }
          )
        }

        // Current turn with contextual file if attached
        val fullUserPrompt = buildString {
          if (!fileContext.isNullOrBlank()) {
            appendLine("=== CONTEXT ATTACHMENT: ${fileName ?: "Authorized Document"} ===")
            appendLine(fileContext.take(15000))
            appendLine("================================================================")
            appendLine()
          }
          append(userMessage)
        }

        contentsArray.put(
          JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
              put(JSONObject().put("text", fullUserPrompt))
            })
          }
        )

        put("contents", contentsArray)

        put("systemInstruction", JSONObject().apply {
          put("parts", JSONArray().apply {
            put(JSONObject().put("text", systemPrompt))
          })
        })

        put("generationConfig", JSONObject().apply {
          put("temperature", temperature.toDouble().coerceIn(0.1, 1.0))
        })
      }

      var url = "$STREAM_BASE_URL&key=$apiKey"
      var request = Request.Builder()
        .url(url)
        .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
        .build()

      var response = okHttpClient.newCall(request).execute()

      if (!response.isSuccessful && response.code == 404) {
        Log.w(TAG, "Primary stream model 404, retrying with fallback stream model: $FALLBACK_MODEL")
        url = "$FALLBACK_STREAM_BASE_URL&key=$apiKey"
        request = Request.Builder()
          .url(url)
          .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
          .build()
        response = okHttpClient.newCall(request).execute()
      }

      if (!response.isSuccessful) {
        val errBody = response.body?.string() ?: ""
        Log.w(TAG, "Gemini SSE Stream error ${response.code}: $errBody")
        val errorMsg = when (response.code) {
          429 -> "Rate limit reached (HTTP 429). Please wait a moment and tap Retry."
          400, 403 -> "Invalid or unauthorized Gemini API key. Please check your key in Settings."
          404 -> "Gemini model is currently unavailable (HTTP 404). Please try again shortly."
          else -> "Gemini API call failed (HTTP ${response.code}). Tap Retry to generate again."
        }
        return@withContext executeUnaryFallback(requestBodyJson, apiKey, errorMsg, onChunkReceived)
      }

      val accumulatedText = StringBuilder()
      val reader = response.body?.byteStream()?.bufferedReader() ?: return@withContext Result.failure(
        Exception("Empty response body from stream")
      )

      reader.use { bufReader ->
        var line = bufReader.readLine()
        while (line != null) {
          currentCoroutineContext().ensureActive()
          val trimmed = line.trim()
          if (trimmed.startsWith("data:")) {
            val jsonPayload = trimmed.removePrefix("data:").trim()
            if (jsonPayload.isNotBlank() && jsonPayload != "[DONE]") {
              try {
                val chunkJson = JSONObject(jsonPayload)
                val candidates = chunkJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val chunkPart = parts?.optJSONObject(0)
                val textChunk = chunkPart?.optString("text")

                if (!textChunk.isNullOrEmpty()) {
                  accumulatedText.append(textChunk)
                  onChunkReceived(textChunk)
                }
              } catch (_: Exception) {
                // Partial JSON chunk skip
              }
            }
          }
          line = bufReader.readLine()
        }
      }

      val resultText = accumulatedText.toString()
      if (resultText.isNotBlank()) {
        Result.success(resultText)
      } else {
        executeUnaryFallback(
          requestBodyJson = requestBodyJson,
          apiKey = apiKey,
          errorMessageOnFail = "No response content received from Gemini. Please tap Retry.",
          onChunkReceived = onChunkReceived
        )
      }
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Log.e(TAG, "Streaming connection error", e)
      Result.failure(
        Exception("Network error: ${e.message ?: "Unable to connect to Gemini API"}. Please check your connection and tap Retry.", e)
      )
    }
  }

  /**
   * Unary non-streaming fallback when SSE streaming is unavailable.
   */
  private suspend fun executeUnaryFallback(
    requestBodyJson: JSONObject,
    apiKey: String,
    errorMessageOnFail: String,
    onChunkReceived: (String) -> Unit
  ): Result<String> {
    return try {
      val unaryUrl = "$BASE_URL?key=$apiKey"
      val request = Request.Builder()
        .url(unaryUrl)
        .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
        .build()
      val response = okHttpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val respBody = response.body?.string() ?: ""
        val respJson = JSONObject(respBody)
        val candidates = respJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text")
        if (!text.isNullOrBlank()) {
          onChunkReceived(text)
          return Result.success(text)
        }
      }
      Result.failure(Exception(errorMessageOnFail))
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      Result.failure(Exception(errorMessageOnFail, e))
    }
  }

  /**
   * Synchronous single-turn chat with Google Gemini.
   */
  suspend fun chatWithGemini(userMessage: String, customApiKey: String? = null): Result<String> {
    var accumulated = ""
    return streamChatWithGemini(
      userMessage = userMessage,
      customApiKey = customApiKey,
      onChunkReceived = { accumulated += it }
    )
  }

  /**
   * Generates tailored text transformation for academic writing tools:
   * Essay Writer, Paraphraser, Grammar Checker, Summarizer, Tone Changer, Content Generator.
   * Calls real Google Gemini API and handles errors cleanly.
   */
  suspend fun generateWritingTransformation(
    tool: String,
    sourceText: String,
    customApiKey: String? = null
  ): String = withContext(Dispatchers.IO) {
    if (sourceText.isBlank()) return@withContext "Please enter source text to process."

    val apiKey = getActiveApiKey(customApiKey)
    if (!isApiKeyConfigured(customApiKey)) {
      return@withContext "Gemini API key is not configured. Please enter your API key in Settings or AI Studio Secrets to use $tool."
    }

    val taskInstruction = when (tool) {
      "Paraphraser" -> "Rephrase the following academic passage with sophisticated vocabulary and distinct syntactic structure while preserving core meaning."
      "Summarizer" -> "Provide a concise, dense academic summary extracting key arguments, findings, and implications."
      "Grammar Checker" -> "Proofread and correct all grammar, punctuation, and stylistic inconsistencies, returning the polished version followed by a bulleted changelog."
      "Essay Writer" -> "Develop the following prompt or argument into a well-structured academic essay section with an introduction, thesis, supporting evidence, and analytical conclusion."
      "Tone Changer" -> "Convert the tone of the following text into authoritative, publication-ready formal academic English."
      else -> "Generate comprehensive, structured scholarly content expanding upon the following ideas."
    }

    val requestBody = JSONObject().apply {
      put("contents", JSONArray().apply {
        put(JSONObject().apply {
          put("parts", JSONArray().apply {
            put(JSONObject().put("text", "$taskInstruction\n\nInput Text:\n\"\"\"\n$sourceText\n\"\"\"\n\nRespond directly with the final output without conversational greetings."))
          })
        })
      })
    }

    try {
      var url = "$BASE_URL?key=$apiKey"
      var request = Request.Builder()
        .url(url)
        .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
        .build()

      var response = okHttpClient.newCall(request).execute()
      var respString = response.body?.string() ?: ""

      if (!response.isSuccessful && response.code == 404) {
        url = "$FALLBACK_BASE_URL?key=$apiKey"
        request = Request.Builder()
          .url(url)
          .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
          .build()
        response = okHttpClient.newCall(request).execute()
        respString = response.body?.string() ?: ""
      }

      if (response.isSuccessful) {
        val respJson = JSONObject(respString)
        val candidates = respJson.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
          val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
          val text = parts?.getJSONObject(0)?.optString("text")
          if (!text.isNullOrBlank()) return@withContext text.trim()
        }
      }

      when (response.code) {
        429 -> "Rate limit reached (HTTP 429). Please wait a moment and try again."
        400, 403 -> "Invalid or unauthorized Gemini API key. Please check your key in Settings."
        else -> "Gemini API request failed (HTTP ${response.code}). Please verify your connection and try again."
      }
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      Log.e(TAG, "Writing transformation network error", e)
      "Network connection error: ${e.message ?: "Unable to connect to Gemini API"}. Please try again."
    }
  }
}

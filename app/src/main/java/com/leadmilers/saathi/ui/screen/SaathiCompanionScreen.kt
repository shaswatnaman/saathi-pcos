package com.leadmilers.saathi.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadmilers.saathi.BuildConfig
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.ml.RiskScorer
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class CompanionQuestion(
    val id: String,
    val icon: ImageVector,
    val question: String,
    val description: String,
)

private val QUESTIONS = listOf(
    CompanionQuestion(
        id = "cycle_phase",
        icon = Icons.Outlined.AutoAwesome,
        question = "What does my cycle phase mean right now?",
        description = "Understand your hormonal window",
    ),
    CompanionQuestion(
        id = "fatigue",
        icon = Icons.Outlined.BatteryChargingFull,
        question = "Why have I been so tired lately?",
        description = "Based on your recent check-ins",
    ),
    CompanionQuestion(
        id = "skin",
        icon = Icons.Outlined.Face,
        question = "What's going on with my skin?",
        description = "Pattern from your scan history",
    ),
    CompanionQuestion(
        id = "doctor_prep",
        icon = Icons.Outlined.LocalHospital,
        question = "Help me prepare for a doctor visit.",
        description = "Summary of what's worth bringing up",
    ),
)

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

@Composable
fun SaathiCompanionScreen() {
    val context = LocalContext.current
    val repo    = remember { (context.applicationContext as SaathiApp).repository }
    val scope   = rememberCoroutineScope()

    var selectedId   by remember { mutableStateOf<String?>(null) }
    var loadingId    by remember { mutableStateOf<String?>(null) }
    var answerMap    by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var errorId      by remember { mutableStateOf<String?>(null) }

    // Collect local data snapshot for prompts
    val symptoms by repo.recentSymptomLogs(7).collectAsState(initial = emptyList())
    val cycles   by repo.recentCycleLogs(3).collectAsState(initial = emptyList())
    val risks    by repo.recentRiskAssessments(1).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(20.dp))

        // Header
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Saathi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your health companion.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select a question and Saathi will look at your recent data to give you a thoughtful answer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        // Question cards
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QUESTIONS.forEach { q ->
                val isSelected = selectedId == q.id
                val isLoading  = loadingId == q.id
                val answer     = answerMap[q.id]
                val hasError   = errorId == q.id

                QuestionCard(
                    question   = q,
                    isSelected = isSelected,
                    isLoading  = isLoading,
                    answer     = answer,
                    hasError   = hasError,
                    onClick    = {
                        if (isSelected) {
                            selectedId = null
                        } else {
                            selectedId = q.id
                            if (answer == null && !isLoading) {
                                loadingId = q.id
                                errorId   = null
                                scope.launch {
                                    try {
                                        val prompt = buildPrompt(
                                            questionId = q.id,
                                            question   = q.question,
                                            symptoms   = symptoms,
                                            cycles     = cycles,
                                            riskScore  = risks.firstOrNull()?.totalScore,
                                            riskLevel  = risks.firstOrNull()?.riskLevel,
                                        )
                                        val response = callCompanion(prompt)
                                        answerMap = answerMap + (q.id to response)
                                    } catch (e: Exception) {
                                        errorId = q.id
                                    } finally {
                                        loadingId = null
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Privacy note
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SaathiDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Answers are generated by Gemini based only on data you've logged. " +
                "This is not medical advice — always discuss significant changes with your doctor.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun QuestionCard(
    question: CompanionQuestion,
    isSelected: Boolean,
    isLoading: Boolean,
    answer: String?,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Surface(
        shape  = MaterialTheme.shapes.large,
        color  = if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                 else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.large,
            )
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        question.icon,
                        contentDescription = null,
                        tint   = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        question.question,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        question.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (isSelected) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(
                visible = isSelected,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaathiDivider()
                    when {
                        isLoading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Looking at your data…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        hasError -> Text(
                            "Couldn't get a response right now. Please try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaathiError,
                        )
                        answer != null -> StructuredAnswer(answer)
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredAnswer(raw: String) {
    val paragraphs = raw.trim().split("\n\n").filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paragraphs.forEach { para ->
            val cleaned = para.trim().removePrefix("-").trim()
            if (cleaned.isNotBlank()) {
                Text(
                    cleaned,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun buildPrompt(
    questionId: String,
    question: String,
    symptoms: List<com.leadmilers.saathi.data.entity.SymptomLog>,
    cycles: List<com.leadmilers.saathi.data.entity.CycleLog>,
    riskScore: Int?,
    riskLevel: String?,
): String {
    val dataBlock = buildString {
        if (symptoms.isNotEmpty()) {
            val avgFatigue = symptoms.map { it.fatigue }.average()
            val avgAcne    = symptoms.map { it.acneScore }.average()
            appendLine("Recent data (last ${symptoms.size} check-ins):")
            appendLine("- Average fatigue: %.1f/5".format(avgFatigue))
            appendLine("- Average skin activity: %.0f%%".format(avgAcne * 100))
            val skinDarkeningCount = symptoms.count { it.skinDarkening }
            val hairIssuesCount    = symptoms.count { it.hairIssues }
            if (skinDarkeningCount > 0) appendLine("- Skin darkening flagged on $skinDarkeningCount days")
            if (hairIssuesCount > 0)    appendLine("- Hair concerns flagged on $hairIssuesCount days")
        }
        if (cycles.isNotEmpty()) {
            val latest = cycles.first()
            val daysSince = ((System.currentTimeMillis() - latest.date) / 86_400_000L + 1L).toInt()
            appendLine("Cycle: Day $daysSince of ${latest.cycleLength}")
        }
        if (riskScore != null && riskLevel != null) {
            appendLine("Current PCOS risk: $riskLevel ($riskScore/12)")
        }
    }

    return """
You are Saathi, a thoughtful health companion for someone managing PCOS. Answer the following question based only on the user's data below. Be warm, clear, and concise — about 3 short paragraphs. Never be alarmist, never give a diagnosis. End with one practical suggestion.

User's question: $question

$dataBlock

Answer:
    """.trimIndent()
}

private suspend fun callCompanion(prompt: String): String = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("model", "google/gemini-2.5-pro")
        put("max_tokens", 512)
        put("temperature", 1.0)
        put("messages", JSONArray().put(
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }
        ))
    }.toString()

    val request = Request.Builder()
        .url("https://openrouter.ai/api/v1/chat/completions")
        .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
        .addHeader("Content-Type", "application/json")
        .post(payload.toRequestBody("application/json".toMediaType()))
        .build()

    val response  = httpClient.newCall(request).execute()
    val body      = response.body?.string() ?: throw Exception("Empty response")
    val root      = JSONObject(body)
    val choices   = root.optJSONArray("choices") ?: throw Exception("No choices in response")
    val message   = choices.getJSONObject(0).getJSONObject("message")
    message.getString("content").trim()
}

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.launch

// ─── AI safety types ─────────────────────────────────────────────────────────

private enum class BlockType { EMERGENCY, CLINICAL, OFFTOPIC }

private data class GuardResult(
    val blocked: Boolean,
    val type: BlockType? = null,
    val message: String = "",
)

private data class ChatMsg(
    val text: String,
    val isUser: Boolean,
    val refusalType: BlockType? = null,
)

// ─── Pre-flight keyword guard — runs before hitting the model ─────────────────

private val EMERGENCY_KEYWORDS = listOf(
    "suicide", "suicidal", "kill myself", "end my life", "take my life",
    "hurt myself", "harm myself", "self harm", "self-harm", "want to die",
    "not worth living", "no reason to live", "overdose on purpose",
    "cutting myself", "starving myself",
)

private val CLINICAL_REQUEST_KEYWORDS = listOf(
    "diagnose me", "do i have", "is this cancer", "do i have pcos",
    "am i pregnant", "could i be pregnant", "am i sick",
    "what medication", "which medicine", "which tablet", "which pill",
    "should i take", "can i take", " mg ", "what dose", "what dosage",
    "stop taking my medication", "change my medication", "my prescription",
    "what does my result", "interpret my result", "my blood test shows",
    "my lab report", "my ultrasound says", "my scan shows",
    "prescribe me", "what treatment should", "how to cure",
    "will i get pregnant", "can i conceive", "fertility treatment",
)

private val OFFTOPIC_KEYWORDS = listOf(
    "recipe", "cooking", "weather forecast", "cricket score",
    "football score", "ipl score", "movie review", "song lyrics",
    "tell me a joke", "write code", "write an email", "homework",
    "capital of", "stock price", "share price", "translate this",
    "history of", "who is the president", "solve this equation",
    "math problem", "write an essay",
)

private fun guardInput(text: String): GuardResult {
    if (text.isBlank()) return GuardResult(blocked = true)
    if (text.length > 400) return GuardResult(
        blocked = true,
        type = BlockType.OFFTOPIC,
        message = "Please keep your question short so Saathi can give a clear observation."
    )
    val lower = text.lowercase()
    if (EMERGENCY_KEYWORDS.any { lower.contains(it) }) return GuardResult(
        blocked = true,
        type = BlockType.EMERGENCY,
        message = "⚠️ If you're in distress, please reach out right now:\n\n• iCall — 9152987821\n• Vandrevala Foundation — 1860-2662-345 (24/7)\n• NIMHANS Helpline — 080-46110007\n\nSaathi is a wellness tracker, not a crisis service. Please speak to a trusted person or a mental health professional."
    )
    if (CLINICAL_REQUEST_KEYWORDS.any { lower.contains(it) }) return GuardResult(
        blocked = true,
        type = BlockType.CLINICAL,
        message = "Saathi can only share observations from your logged data — not diagnose conditions or recommend medications. For that question, please consult a qualified doctor or healthcare provider."
    )
    if (OFFTOPIC_KEYWORDS.any { lower.contains(it) }) return GuardResult(
        blocked = true,
        type = BlockType.OFFTOPIC,
        message = "Saathi can only discuss observations about your health data — cycle patterns, energy levels, skin, and symptoms. For other topics, please try a general assistant."
    )
    return GuardResult(blocked = false)
}

// ─── Companion question card type ────────────────────────────────────────────

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

@Composable
fun SaathiCompanionScreen() {
    val context    = LocalContext.current
    val repo       = remember { (context.applicationContext as SaathiApp).repository }
    val aiManager  = remember { (context.applicationContext as SaathiApp).localAiManager }
    val scope      = rememberCoroutineScope()

    // Model state
    var modelReady by remember { mutableStateOf(aiManager.isModelReady) }
    var modelLoading by remember { mutableStateOf(false) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var loadingId  by remember { mutableStateOf<String?>(null) }
    var answerMap  by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var errorId    by remember { mutableStateOf<String?>(null) }

    // Free-form chat
    var chatInput    by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf(listOf<ChatMsg>()) }
    var chatLoading  by remember { mutableStateOf(false) }

    val symptoms by repo.recentSymptomLogs(7).collectAsState(initial = emptyList())
    val cycles   by repo.recentCycleLogs(3).collectAsState(initial = emptyList())
    val risks    by repo.recentRiskAssessments(1).collectAsState(initial = emptyList())

    // If model just became ready, load it into memory
    LaunchedEffect(modelReady) {
        if (modelReady && !modelLoading) {
            modelLoading = true
            try { aiManager.ensureLoaded() } catch (_: Exception) {}
            modelLoading = false
        }
    }

    // Show download screen until model is present
    if (!modelReady) {
        ModelSetupScreen(onModelReady = { modelReady = true })
        return
    }

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
                "Select a question and Saathi will look at your recent data to give you a thoughtful answer — entirely on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // On-device badge
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Text("Runs on-device · Gemma 2B",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (modelLoading) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
        }

        Spacer(Modifier.height(20.dp))

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
                                        // Stream tokens into answerMap as they arrive
                                        // Re-prepend the seeded first token we put in the prompt
                                        val sb = StringBuilder("Observation 1:")
                                        aiManager.generateAsFlow(prompt).collect { token ->
                                            sb.append(token)
                                            answerMap = answerMap + (q.id to cleanGemmaOutput(sb.toString()))
                                        }
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

        Spacer(Modifier.height(28.dp))

        // ── Free-form chat ────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "ASK ANYTHING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ask about your health patterns — Saathi shares observations, not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // Chat message history
            if (chatMessages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chatMessages.forEach { msg ->
                        if (msg.isUser) {
                            // User bubble — right-aligned
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    Text(
                                        msg.text,
                                        style    = MaterialTheme.typography.bodySmall,
                                        color    = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        } else {
                            // Saathi bubble — left-aligned
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint     = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                if (msg.refusalType != null) {
                                        RefusalBubble(msg.refusalType, msg.text)
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            if (msg.text.isBlank() && chatLoading) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier    = Modifier.size(12.dp),
                                                        strokeWidth = 2.dp,
                                                        color       = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Text("Thinking…",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            } else {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    StructuredAnswer(msg.text)
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Input row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value         = chatInput,
                    onValueChange = { if (it.length <= 400) chatInput = it },
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("e.g. Why am I so tired this week?",
                        style = MaterialTheme.typography.bodySmall) },
                    shape         = RoundedCornerShape(16.dp),
                    maxLines      = 3,
                    textStyle     = MaterialTheme.typography.bodySmall,
                    enabled       = !chatLoading,
                    supportingText = if (chatInput.length > 320) {
                        { Text("${chatInput.length}/400",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chatInput.length >= 400) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else null,
                )
                FilledIconButton(
                    onClick  = {
                        val q = chatInput.trim()
                        if (q.isBlank() || chatLoading) return@FilledIconButton
                        val guard = guardInput(q)
                        chatInput = ""
                        val userMsg = ChatMsg(q, isUser = true)
                        if (guard.blocked) {
                            // Show refusal immediately — no model call needed
                            chatMessages = chatMessages + userMsg +
                                ChatMsg(guard.message, isUser = false, refusalType = guard.type)
                            return@FilledIconButton
                        }
                        val botMsg = ChatMsg("", isUser = false)
                        chatMessages = chatMessages + userMsg + botMsg
                        chatLoading = true
                        scope.launch {
                            try {
                                val prompt = buildChatPrompt(q, symptoms, cycles,
                                    risks.firstOrNull()?.totalScore,
                                    risks.firstOrNull()?.riskLevel)
                                val sb = StringBuilder("Observation 1:")
                                aiManager.generateAsFlow(prompt).collect { token ->
                                    sb.append(token)
                                    val updated = chatMessages.toMutableList()
                                    updated[updated.lastIndex] = ChatMsg(cleanGemmaOutput(sb.toString()), isUser = false)
                                    chatMessages = updated
                                }
                            } catch (e: Exception) {
                                val updated = chatMessages.toMutableList()
                                updated[updated.lastIndex] = ChatMsg(
                                    "Saathi couldn't respond right now. Please try again.",
                                    isUser = false,
                                )
                                chatMessages = updated
                            } finally {
                                chatLoading = false
                            }
                        }
                    },
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(52.dp),
                    enabled  = !chatLoading && chatInput.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = "Send",
                        modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SaathiDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Saathi shares observations from your logged data using Gemma 2B, running entirely on your device. " +
                "This is not medical advice — consult a doctor for diagnosis or treatment.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun RefusalBubble(type: BlockType, message: String) {
    val (bg, iconTint) = when (type) {
        BlockType.EMERGENCY -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        BlockType.CLINICAL  -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        BlockType.OFFTOPIC  -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        color = bg,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (type == BlockType.EMERGENCY) {
                Text(
                    "You're not alone",
                    style = MaterialTheme.typography.labelMedium,
                    color = iconTint,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = iconTint,
            )
        }
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
                        isLoading && answer == null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Thinking on-device…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        hasError -> Text(
                            "Couldn't generate a response. Make sure the model loaded correctly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SaathiError,
                        )
                        answer != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StructuredAnswer(answer)
                            // Blinking cursor while still streaming
                            if (isLoading) {
                                Text("▍", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredAnswer(raw: String) {
    val lines = raw.trim().lines().filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Observation", ignoreCase = true) -> {
                    val colon = trimmed.indexOf(':')
                    if (colon > 0 && colon < trimmed.length - 1) {
                        val label = trimmed.substring(0, colon + 1)
                        val body  = trimmed.substring(colon + 1).trim()
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Column {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                if (body.isNotBlank())
                                    Text(body, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    } else {
                        Text(trimmed, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                trimmed.startsWith("Note:", ignoreCase = true) -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            trimmed,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                else -> Text(trimmed, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun buildDataBlock(
    symptoms: List<com.leadmilers.saathi.data.entity.SymptomLog>,
    cycles: List<com.leadmilers.saathi.data.entity.CycleLog>,
    riskScore: Int?,
    riskLevel: String?,
) = buildString {
    if (symptoms.isNotEmpty()) {
        val avgFatigue = symptoms.map { it.fatigue }.average()
        val avgAcne    = symptoms.map { it.acneScore }.average()
        appendLine("Logged data (last ${symptoms.size} entries):")
        appendLine("• Fatigue avg: %.1f/5".format(avgFatigue))
        appendLine("• Skin activity avg: %.0f%%".format(avgAcne * 100))
        val skinDark = symptoms.count { it.skinDarkening }
        val hair     = symptoms.count { it.hairIssues }
        if (skinDark > 0) appendLine("• Skin darkening: $skinDark days")
        if (hair > 0)     appendLine("• Hair concerns: $hair days")
    }
    if (cycles.isNotEmpty()) {
        val latest    = cycles.first()
        val daysSince = ((System.currentTimeMillis() - latest.date) / 86_400_000L + 1L).toInt()
        appendLine("• Cycle day $daysSince of ${latest.cycleLength}")
    }
    if (riskScore != null && riskLevel != null) {
        appendLine("• PCOS risk score: $riskLevel ($riskScore/12)")
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
    val data = buildDataBlock(symptoms, cycles, riskScore, riskLevel)
    // Seed the model's first token to suppress "Sure, here's..." preamble.
    // Gemma 2B continues from wherever the model turn starts.
    return buildGemmaPrompt(question, data)
}

fun buildChatPrompt(
    userMessage: String,
    symptoms: List<com.leadmilers.saathi.data.entity.SymptomLog>,
    cycles: List<com.leadmilers.saathi.data.entity.CycleLog>,
    riskScore: Int?,
    riskLevel: String?,
): String {
    val data = buildDataBlock(symptoms, cycles, riskScore, riskLevel)
    return buildGemmaPrompt(userMessage, data)
}

private fun buildGemmaPrompt(question: String, dataBlock: String): String = """<start_of_turn>user
You are Saathi, a health data companion. You have ONE job: share brief, neutral observations drawn from the user's logged data below. You do NOT diagnose, prescribe, treat, or speculate about conditions. You do NOT answer questions outside health-tracking topics. If the question asks for diagnosis, medication, or treatment, your entire response must be ONLY the Note line.

Strict output format — start directly, no intro sentence:
Observation 1: [one factual sentence about what the data shows]
Observation 2: [one sentence about a visible pattern or change over time]
Observation 3: [one practical lifestyle suggestion — hydration, sleep, logging — nothing medical]
Note: This is an observation from logged data, not medical advice. Please consult a doctor for any health concerns.

Rules:
- Never use words: diagnose, treatment, medication, prescription, therapy, surgery, cure, disease
- Never say "you should see a doctor" in Observation lines — only in the Note
- If you cannot answer from the data, write: Note: Saathi doesn't have enough data to observe a pattern here. Keep logging for better insights.
- Maximum 20 words per line

User question: $question

$dataBlock<end_of_turn>
<start_of_turn>model
Observation 1:"""

private val PREAMBLE_PATTERNS = listOf(
    "Sure, here", "Of course", "I'd be happy", "Great question",
    "Certainly", "Based on the", "As Saathi", "Hello!", "Hi there",
    "I understand", "That's a great", "Absolutely", "I'm here to help",
)

private val CLINICAL_LEAK_PATTERNS = listOf(
    "you should take", "i recommend taking", "consider taking",
    "medication", "prescription", "dosage", "mg per day",
    "you have pcos", "you have diabetes", "you have",
    "this is a symptom of", "this indicates",
    "consult a specialist",  // only allowed in Note line, not Observations
)

/** Strip preambles and any clinical content that leaked through the model output. */
fun cleanGemmaOutput(raw: String): String {
    var s = raw.trim()

    // Strip opening preamble on first line
    for (p in PREAMBLE_PATTERNS) {
        if (s.startsWith(p, ignoreCase = true)) {
            val cutAt = s.indexOf('\n').takeIf { it > 0 } ?: s.indexOf('.').takeIf { it > 0 }
            if (cutAt != null) s = s.substring(cutAt + 1).trimStart()
            break
        }
    }

    // Rebuild lines, dropping any that contain clinical leaks (except Note line)
    val lines = s.lines()
    val cleaned = lines.mapIndexed { i, line ->
        val lower = line.lowercase()
        val isNote = lower.trimStart().startsWith("note")
        if (!isNote && CLINICAL_LEAK_PATTERNS.any { lower.contains(it) }) {
            // Replace clinical line with a safe placeholder
            if (i == 0) "Observation 1: Your logged data shows recent health activity worth tracking."
            else null
        } else {
            line
        }
    }.filterNotNull()

    // Truncate after the Note line — nothing after it
    val result = StringBuilder()
    var noteFound = false
    for (line in cleaned) {
        if (noteFound) break
        result.appendLine(line)
        if (line.trim().startsWith("Note:", ignoreCase = true)) noteFound = true
    }
    return result.toString().trimEnd()
}

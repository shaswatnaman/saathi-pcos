package com.leadmilers.saathi.ui.screen.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.R
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.companion.QrHelper
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.prefs.UserPrefs
import com.leadmilers.saathi.prefs.UserRole
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*

private sealed class OnboardingPage {
    object Welcome         : OnboardingPage()
    object RoleSelect      : OnboardingPage()
    // Self path
    object SelfName        : OnboardingPage()
    object SelfGoals       : OnboardingPage()
    object SelfCycle       : OnboardingPage()
    object SelfDone        : OnboardingPage()
    // Companion path
    object CompanionName   : OnboardingPage()
    object CompanionRole   : OnboardingPage()
    object CompanionPairing: OnboardingPage()
    object CompanionDone   : OnboardingPage()
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }
    val repo    = remember { (context.applicationContext as SaathiApp).repository }
    val scope   = rememberCoroutineScope()

    var page by remember { mutableStateOf<OnboardingPage>(OnboardingPage.Welcome) }
    var selfName        by remember { mutableStateOf("") }
    var companionName   by remember { mutableStateOf("") }
    var companionRole   by remember { mutableStateOf("Partner") }
    var selectedGoals   by remember { mutableStateOf(setOf<String>()) }
    var cycleStartDate  by remember { mutableStateOf<Long?>(null) }
    var cycleLength     by remember { mutableIntStateOf(28) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
            slideOutHorizontally { -it } + fadeOut()
        },
        label = "onboarding",
    ) { current ->
        when (current) {
            OnboardingPage.Welcome -> WelcomePage {
                page = OnboardingPage.RoleSelect
            }

            OnboardingPage.RoleSelect -> RoleSelectPage(
                onSelf = { page = OnboardingPage.SelfName },
                onCompanion = { page = OnboardingPage.CompanionName },
            )

            // ── Self path ─────────────────────────────────────────────────────
            OnboardingPage.SelfName -> NamePage(
                title       = "What should we call you?",
                placeholder = "Your first name",
                value       = selfName,
                onChange    = { selfName = it },
                onBack      = { page = OnboardingPage.RoleSelect },
                onContinue  = { page = OnboardingPage.SelfGoals },
            )

            OnboardingPage.SelfGoals -> GoalsPage(
                selected   = selectedGoals,
                onChange   = { selectedGoals = it },
                onBack     = { page = OnboardingPage.SelfName },
                onContinue = { page = OnboardingPage.SelfCycle },
            )

            OnboardingPage.SelfCycle -> CyclePage(
                selectedDate  = cycleStartDate,
                onDate        = { cycleStartDate = it },
                cycleLength   = cycleLength,
                onCycleLength = { cycleLength = it },
                onBack        = { page = OnboardingPage.SelfGoals },
                onContinue    = {
                    scope.launch {
                        prefs.role      = UserRole.SELF
                        prefs.userName  = selfName
                        prefs.goals     = selectedGoals
                        prefs.cycleLength = cycleLength
                        cycleStartDate?.let { d ->
                            prefs.lastPeriodDate = d
                            repo.insertCycleLog(CycleLog(date = d, cycleLength = cycleLength, flowIntensity = "Medium"))
                        }
                        prefs.onboardingComplete = true
                    }
                    page = OnboardingPage.SelfDone
                },
            )

            OnboardingPage.SelfDone -> SelfDonePage(name = selfName, onStart = onComplete)

            // ── Companion path ─────────────────────────────────────────────────
            OnboardingPage.CompanionName -> NamePage(
                title       = "What should we call you?",
                placeholder = "Your first name",
                value       = companionName,
                onChange    = { companionName = it },
                onBack      = { page = OnboardingPage.RoleSelect },
                onContinue  = { page = OnboardingPage.CompanionRole },
            )

            OnboardingPage.CompanionRole -> CompanionRolePage(
                selected   = companionRole,
                onChange   = { companionRole = it },
                onBack     = { page = OnboardingPage.CompanionName },
                onContinue = { page = OnboardingPage.CompanionPairing },
            )

            OnboardingPage.CompanionPairing -> CompanionPairingPage(
                onBack = { page = OnboardingPage.CompanionRole },
                onPaired = { primaryDeviceId, token ->
                    prefs.role         = UserRole.COMPANION
                    prefs.userName     = companionName
                    prefs.partnerName  = ""
                    prefs.pairedDeviceId = primaryDeviceId
                    prefs.pairingToken = token
                    prefs.isPaired     = true
                    prefs.onboardingComplete = true
                    page = OnboardingPage.CompanionDone
                },
                onSkip = {
                    prefs.role         = UserRole.COMPANION
                    prefs.userName     = companionName
                    prefs.onboardingComplete = true
                    onComplete()
                },
            )

            OnboardingPage.CompanionDone -> CompanionDonePage(onStart = onComplete)
        }
    }
}

// ── Welcome ────────────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    val alpha by produceState(initialValue = 0f) {
        kotlinx.coroutines.delay(100)
        value = 1f
    }
    val scale by produceState(initialValue = 0.88f) {
        kotlinx.coroutines.delay(100)
        value = 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val animAlpha by animateFloatAsState(
            targetValue  = alpha,
            animationSpec = tween(800, easing = EaseOutCubic),
            label        = "logo_alpha",
        )
        val animScale by animateFloatAsState(
            targetValue  = scale,
            animationSpec = tween(800, easing = EaseOutCubic),
            label        = "logo_scale",
        )

        Image(
            painter  = painterResource(R.drawable.saathi_logo),
            contentDescription = "Saathi",
            modifier = Modifier
                .size(110.dp)
                .alpha(animAlpha)
                .scale(animScale),
        )

        Spacer(Modifier.height(36.dp))

        Text(
            "Your health,\nunderstood.",
            style      = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground,
            modifier   = Modifier.alpha(animAlpha),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "A private space to understand your\ncycle, wellness and yourself.",
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier  = Modifier.alpha(animAlpha),
        )

        Spacer(Modifier.height(64.dp))

        SaathiPrimaryButton(
            text     = "Get started",
            onClick  = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(animAlpha),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Your data stays on your device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(animAlpha),
        )
    }
}

// ── Role select ───────────────────────────────────────────────────────────────

@Composable
private fun RoleSelectPage(onSelf: () -> Unit, onCompanion: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(48.dp))

        Image(
            painter = painterResource(R.drawable.saathi_logo),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )

        Spacer(Modifier.height(28.dp))

        Text(
            "How would you like\nto use Saathi?",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(36.dp))

        RoleCard(
            icon        = Icons.Outlined.Favorite,
            title       = "I'm here for myself",
            subtitle    = "Track my cycle, symptoms, wellness\nand health journey.",
            onClick     = onSelf,
            accentColor = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        RoleCard(
            icon        = Icons.Outlined.FavoriteBorder,
            title       = "I'm here for my partner",
            subtitle    = "Understand, support and be there\nfor someone I love.",
            onClick     = onCompanion,
            accentColor = MaterialTheme.colorScheme.secondary,
        )

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color,
) {
    Surface(
        onClick = onClick,
        shape   = MaterialTheme.shapes.extraLarge,
        color   = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Name ──────────────────────────────────────────────────────────────────────

@Composable
private fun NamePage(
    title: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingScaffold(
        onBack    = onBack,
        progress  = 0.33f,
        bottomBar = {
            SaathiPrimaryButton(
                text     = "Continue",
                onClick  = onContinue,
                enabled  = value.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    ) {
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            placeholder   = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            singleLine    = true,
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

// ── Goals ─────────────────────────────────────────────────────────────────────

private val GOALS = listOf(
    "Understanding my cycle",
    "Tracking symptoms",
    "PCOS & hormonal wellness",
    "Mood & energy",
    "Building healthier routines",
    "Understanding my patterns",
    "Just exploring",
)

@Composable
private fun GoalsPage(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingScaffold(
        onBack   = onBack,
        progress = 0.55f,
        bottomBar = {
            SaathiPrimaryButton(
                text     = "Continue",
                onClick  = onContinue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    ) {
        Spacer(Modifier.height(20.dp))
        Text("What would you\nlike help with?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text("Pick as many as you like. You can always change this later.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        GOALS.forEach { goal ->
            val isSelected = goal in selected
            Surface(
                onClick = {
                    onChange(if (isSelected) selected - goal else selected + goal)
                },
                shape  = MaterialTheme.shapes.medium,
                color  = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(goal,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                    if (isSelected) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Cycle ─────────────────────────────────────────────────────────────────────

@Composable
private fun CyclePage(
    selectedDate: Long?,
    onDate: (Long?) -> Unit,
    cycleLength: Int,
    onCycleLength: (Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var notSure by remember { mutableStateOf(selectedDate == null) }

    OnboardingScaffold(
        onBack   = onBack,
        progress = 0.77f,
        bottomBar = {
            SaathiPrimaryButton(
                text     = "Continue",
                onClick  = onContinue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Tell us about\nyour cycle",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text("This helps Saathi understand where you are.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))

        SaathiSubheading("When did your last period start?")
        Spacer(Modifier.height(12.dp))

        if (!notSure) {
            // Simple date: show the approximate date as "X days ago" picker
            val daysAgoOptions = listOf(0, 1, 3, 7, 10, 14, 21)
            var selectedDays by remember { mutableIntStateOf(7) }
            LaunchedEffect(selectedDays) {
                onDate(System.currentTimeMillis() - selectedDays * 86_400_000L)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                daysAgoOptions.forEach { d ->
                    SaathiChip(
                        text     = if (d == 0) "Today" else "$d days ago",
                        selected = selectedDays == d,
                        onClick  = { selectedDays = d },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("I'm not sure", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = notSure, onCheckedChange = { notSure = it; if (it) onDate(null) })
        }

        Spacer(Modifier.height(28.dp))
        SaathiDivider()
        Spacer(Modifier.height(20.dp))
        SaathiSubheading("How long is your usual cycle?")
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$cycleLength days",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { if (cycleLength > 21) onCycleLength(cycleLength - 1) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Less")
                }
                FilledTonalIconButton(onClick = { if (cycleLength < 42) onCycleLength(cycleLength + 1) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "More")
                }
            }
        }
    }
}

// ── Self done ─────────────────────────────────────────────────────────────────

@Composable
private fun SelfDonePage(name: String, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.saathi_logo),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "You're all set${if (name.isNotBlank()) ", $name" else ""}.",
            style     = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We'll help you understand your patterns — without judgment.",
            style    = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(56.dp))
        SaathiPrimaryButton(
            text     = "Let's begin",
            onClick  = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Companion role ────────────────────────────────────────────────────────────

private val COMPANION_ROLES = listOf("Partner", "Husband / Wife", "Boyfriend / Girlfriend", "Other")

@Composable
private fun CompanionRolePage(
    selected: String,
    onChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingScaffold(
        onBack   = onBack,
        progress = 0.55f,
        bottomBar = {
            SaathiPrimaryButton(
                text     = "Continue",
                onClick  = onContinue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Who are you\nsupporting?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text("You're in the right place. Your partner's privacy is in their hands.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        COMPANION_ROLES.forEach { role ->
            val isSelected = role == selected
            Surface(
                onClick  = { onChange(role) },
                shape    = MaterialTheme.shapes.medium,
                color    = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                           else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface)
                    if (isSelected) Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Companion pairing ─────────────────────────────────────────────────────────

@Composable
private fun CompanionPairingPage(
    onBack: () -> Unit,
    onPaired: (deviceId: String, token: String) -> Unit,
    onSkip: () -> Unit,
) {
    var codeInput by remember { mutableStateOf("") }
    var error     by remember { mutableStateOf<String?>(null) }

    OnboardingScaffold(
        onBack   = onBack,
        progress = 0.77f,
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SaathiPrimaryButton(
                    text     = "Connect",
                    enabled  = codeInput.length >= 6,
                    onClick  = {
                        // In production this would validate against the scanned QR or be paired via Nearby.
                        // For now we accept any 6-char token + store deviceId = "primary"
                        if (codeInput.length >= 6) {
                            onPaired("primary_device", codeInput.uppercase())
                        } else {
                            error = "Please enter the 6-character code from your partner's app."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaathiSecondaryButton(
                    text     = "Skip for now",
                    onClick  = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Connect with\nyour partner",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Ask your partner to open Saathi, go to Settings → Add Companion, and share their code with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value         = codeInput.uppercase(),
            onValueChange = { if (it.length <= 6) { codeInput = it; error = null } },
            label         = { Text("Partner's 6-character code") },
            placeholder   = { Text("e.g. A4KX9P") },
            modifier      = Modifier.fillMaxWidth(),
            shape         = MaterialTheme.shapes.medium,
            singleLine    = true,
            isError       = error != null,
            supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your partner's privacy is protected.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("You'll only see information they choose to share with you. They can update or remove your access at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Companion done ────────────────────────────────────────────────────────────

@Composable
private fun CompanionDonePage(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.saathi_logo),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "You're connected.",
            style     = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You can now support each other privately and securely. Everything shared is by choice.",
            style    = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(56.dp))
        SaathiPrimaryButton(
            text     = "Open Saathi",
            onClick  = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Shared scaffold ────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScaffold(
    onBack: () -> Unit,
    progress: Float,
    bottomBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 0.dp, color = MaterialTheme.colorScheme.background) {
                bottomBar()
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                }
                LinearProgressIndicator(
                    progress        = { progress },
                    modifier        = Modifier.weight(1f).padding(horizontal = 12.dp).height(3.dp),
                    color           = MaterialTheme.colorScheme.primary,
                    trackColor      = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    strokeCap       = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Spacer(Modifier.size(48.dp))
            }
            content()
        }
    }
}

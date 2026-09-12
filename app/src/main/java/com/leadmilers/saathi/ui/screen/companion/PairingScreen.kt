package com.leadmilers.saathi.ui.screen.companion

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.R
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.companion.QrHelper
import com.leadmilers.saathi.prefs.UserPrefs
import com.leadmilers.saathi.ui.components.*
import com.leadmilers.saathi.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { (context.applicationContext as SaathiApp).userPrefs }
    val scope   = rememberCoroutineScope()

    // Ensure a pairing token exists for this user
    var token by remember { mutableStateOf(prefs.pairingToken) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(token) {
        if (token.isBlank()) {
            token = QrHelper.generateToken()
            prefs.pairingToken = token
        }
        // Generate QR off main thread
        val deviceId = prefs.deviceId
        val bmp = withContext(Dispatchers.Default) {
            QrHelper.generatePairingQr(deviceId, token, 512)
        }
        qrBitmap = bmp
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add Companion") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back",
                            modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Invite your partner",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Let someone you trust stay connected to the parts of your wellness journey you choose to share.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // QR card
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Logo above QR
                    Image(
                        painter = painterResource(R.drawable.saathi_logo),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )

                    // QR code
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Pairing QR code",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }

                    // Code display
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Or share this code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            token,
                            style      = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                            letterSpacing = 8.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Instructions
            Surface(
                shape    = MaterialTheme.shapes.large,
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InstructionRow("1", "Ask your partner to install Saathi on their phone.")
                    InstructionRow("2", "They open the app and choose \"I'm here for my partner\".")
                    InstructionRow("3", "They enter this code or scan the QR above.")
                    InstructionRow("4", "Once connected, you control what they can see.")
                }
            }

            Spacer(Modifier.height(24.dp))

            // Regenerate token
            TextButton(onClick = {
                scope.launch {
                    token = QrHelper.generateToken()
                    prefs.pairingToken = token
                }
            }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate a new code", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun InstructionRow(step: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(step, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

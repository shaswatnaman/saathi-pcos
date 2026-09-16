package com.leadmilers.saathi.companion

import android.Manifest
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.accompanist.permissions.*

/** All runtime permissions Nearby Connections needs, by Android version. */
fun nearbyPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/**
 * Requests all Nearby-required permissions.
 * [onAllGranted] is a plain (non-composable) callback called when every permission is granted.
 * Shows a one-time rationale dialog if any permission was previously denied.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WithNearbyPermissions(
    onAllGranted: () -> Unit,   // plain callback — set a remembered state flag here
) {
    val permissions = nearbyPermissions()
    val state = rememberMultiplePermissionsState(
        permissions = permissions,
        onPermissionsResult = { results ->
            if (results.values.all { it }) onAllGranted()
        }
    )

    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(state.allPermissionsGranted) {
        when {
            state.allPermissionsGranted -> onAllGranted()
            state.shouldShowRationale   -> showRationale = true
            else                        -> state.launchMultiplePermissionRequest()
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow nearby sync") },
            text  = {
                Text(
                    "Saathi uses Bluetooth and nearby Wi-Fi to sync health data " +
                    "between your device and your partner's — no internet required. " +
                    "Please allow the requested permissions to continue."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    state.launchMultiplePermissionRequest()
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now") }
            }
        )
    }
}

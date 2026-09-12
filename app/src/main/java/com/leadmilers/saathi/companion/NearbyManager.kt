package com.leadmilers.saathi.companion

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "NearbyManager"
private const val SERVICE_ID = "com.leadmilers.saathi.sync"

enum class SyncStatus {
    IDLE, ADVERTISING, DISCOVERING, CONNECTING, SYNCING, SYNCED, ERROR
}

data class NearbyState(
    val status: SyncStatus = SyncStatus.IDLE,
    val statusMessage: String = "",
    val lastSyncAt: Long = 0L,
    val lastPacket: SyncPacket? = null,
)

class NearbyManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state

    private var pairedDeviceId: String = ""
    private var localName: String = ""
    private var onPacketReceived: ((SyncPacket) -> Unit)? = null
    private var pendingPayload: String? = null
    private var connectedEndpointId: String? = null

    fun configure(localName: String, pairedDeviceId: String, onPacket: (SyncPacket) -> Unit) {
        this.localName      = localName
        this.pairedDeviceId = pairedDeviceId
        this.onPacketReceived = onPacket
    }

    // ── Payload / connection callbacks ────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val json  = String(bytes)
                val packet = SyncPacket.fromJson(json) ?: return
                // Validate sender
                if (packet.senderDeviceId != pairedDeviceId) {
                    Log.w(TAG, "Packet from unknown device — ignoring")
                    return
                }
                _state.value = _state.value.copy(
                    status        = SyncStatus.SYNCED,
                    statusMessage = "Synced just now",
                    lastSyncAt    = System.currentTimeMillis(),
                    lastPacket    = packet,
                )
                onPacketReceived?.invoke(packet)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept if the remote endpoint is our paired device
            if (info.endpointName == pairedDeviceId || pairedDeviceId.isEmpty()) {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                _state.value = _state.value.copy(
                    status        = SyncStatus.CONNECTING,
                    statusMessage = "Connecting…",
                )
            } else {
                connectionsClient.rejectConnection(endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                _state.value = _state.value.copy(
                    status        = SyncStatus.SYNCING,
                    statusMessage = "Updating shared information…",
                )
                // If we had a payload queued (primary advertiser), send it now
                pendingPayload?.let { json ->
                    sendPayload(endpointId, json)
                    pendingPayload = null
                }
            } else {
                _state.value = _state.value.copy(
                    status        = SyncStatus.ERROR,
                    statusMessage = "Couldn't sync",
                )
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            if (_state.value.status == SyncStatus.SYNCED) return
            _state.value = _state.value.copy(
                status        = SyncStatus.IDLE,
                statusMessage = "Partner nearby status unavailable",
            )
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.endpointName == pairedDeviceId || pairedDeviceId.isEmpty()) {
                stopDiscovering()
                connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener {
                        _state.value = _state.value.copy(
                            status        = SyncStatus.ERROR,
                            statusMessage = "Couldn't connect",
                        )
                    }
            }
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Primary device: advertise and push a SyncPacket when companion connects. */
    fun advertiseAndPush(packet: SyncPacket) {
        pendingPayload = packet.toJson()
        connectedEndpointId?.let { sendPayload(it, pendingPayload!!); pendingPayload = null; return }

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status        = SyncStatus.ADVERTISING,
                    statusMessage = "Looking for your partner…",
                )
            }
            .addOnFailureListener {
                _state.value = _state.value.copy(
                    status        = SyncStatus.ERROR,
                    statusMessage = "Couldn't start sync",
                )
            }
    }

    /** Companion device: discover and receive a SyncPacket. */
    fun discover() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status        = SyncStatus.DISCOVERING,
                    statusMessage = "Looking for partner's device…",
                )
            }
            .addOnFailureListener {
                _state.value = _state.value.copy(
                    status        = SyncStatus.ERROR,
                    statusMessage = "Couldn't start sync",
                )
            }
    }

    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectedEndpointId = null
        _state.value = NearbyState(status = SyncStatus.IDLE, statusMessage = "")
    }

    private fun stopDiscovering() = connectionsClient.stopDiscovery()

    private fun sendPayload(endpointId: String, json: String) {
        val bytes = Payload.fromBytes(json.toByteArray())
        connectionsClient.sendPayload(endpointId, bytes)
            .addOnSuccessListener {
                _state.value = _state.value.copy(
                    status        = SyncStatus.SYNCED,
                    statusMessage = "Synced just now",
                    lastSyncAt    = System.currentTimeMillis(),
                )
                connectionsClient.disconnectFromEndpoint(endpointId)
                connectedEndpointId = null
            }
    }

    companion object {
        @Volatile private var INSTANCE: NearbyManager? = null
        fun getInstance(context: Context): NearbyManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NearbyManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

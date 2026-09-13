package com.leadmilers.saathi.companion

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LanSyncManager"

/**
 * WiFi-LAN sync via UDP broadcast + plain TCP sockets.
 *
 * Patient (SELF): opens TCP server, broadcasts "saathi-TOKEN:PORT" via UDP every 2s.
 * Gynac: listens on BEACON_PORT for any "saathi-" beacon, connects TCP.
 * Token matching is skipped on the gynac side for demo reliability.
 */
class LanSyncManager(private val context: Context) {

    companion object {
        const val BEACON_PORT = 7423
        private const val BEACON_INTERVAL_MS = 2_000L
        private const val DISCOVER_TIMEOUT_MS = 90_000L

        @Volatile private var INSTANCE: LanSyncManager? = null
        fun getInstance(ctx: Context): LanSyncManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LanSyncManager(ctx.applicationContext).also { INSTANCE = it }
            }
    }

    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state

    private var pairedToken: String = ""
    private var localName: String = ""
    private var onPacketReceived: ((SyncPacket) -> Unit)? = null

    private val isAdvertising = AtomicBoolean(false)
    private val isDiscovering = AtomicBoolean(false)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var beaconSocket: DatagramSocket? = null
    @Volatile private var listenSocket: DatagramSocket? = null

    // ── Public API ──────────────────────────────────────────────────────────────

    fun configure(localName: String, pairedToken: String, onPacket: (SyncPacket) -> Unit) {
        this.localName        = localName
        this.pairedToken      = pairedToken
        this.onPacketReceived = onPacket
    }

    /** SELF/Patient side: open TCP server, broadcast port via UDP. */
    fun advertiseAndPush(packet: SyncPacket) {
        if (isAdvertising.getAndSet(true)) return
        val json = packet.toJson()
        Thread {
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wm.createMulticastLock("saathi_adv").also {
                    it.setReferenceCounted(false); it.acquire()
                }
                val ss = ServerSocket(0)
                serverSocket = ss
                val port = ss.localPort
                Log.d(TAG, "TCP server on port $port")

                Thread { beacon(wm, port) }.start()

                _state.value = _state.value.copy(
                    status        = SyncStatus.ADVERTISING,
                    statusMessage = "Waiting for partner…",
                )
                ss.soTimeout = 120_000
                val client: Socket = ss.accept()

                _state.value = _state.value.copy(status = SyncStatus.SYNCING, statusMessage = "Sending data…")
                stopBeacon()
                PrintWriter(client.getOutputStream(), true).use { it.println(json) }
                client.close()
                ss.close()
                serverSocket = null

                _state.value = _state.value.copy(
                    status        = SyncStatus.SYNCED,
                    statusMessage = "Synced just now",
                    lastSyncAt    = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                Log.w(TAG, "advertise error: ${e.message}")
                stopBeacon()
                if (_state.value.status != SyncStatus.SYNCED)
                    _state.value = _state.value.copy(status = SyncStatus.IDLE, statusMessage = "")
            } finally {
                multicastLock?.release()
                isAdvertising.set(false)
            }
        }.start()
    }

    /** Gynac/Companion side: listen for any saathi UDP beacon, pull data over TCP. */
    fun discover() {
        if (isDiscovering.getAndSet(true)) return
        _state.value = _state.value.copy(
            status        = SyncStatus.DISCOVERING,
            statusMessage = "Looking for patient's device…",
        )
        Thread {
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wm.createMulticastLock("saathi_disc").also {
                    it.setReferenceCounted(false); it.acquire()
                }
                val ds = DatagramSocket(null).also { it.reuseAddress = true }
                ds.bind(InetSocketAddress(BEACON_PORT))
                ds.soTimeout = DISCOVER_TIMEOUT_MS.toInt()
                listenSocket = ds

                val buf   = ByteArray(512)
                val dgram = DatagramPacket(buf, buf.size)
                Log.d(TAG, "Listening for any saathi beacon on UDP $BEACON_PORT")

                while (isDiscovering.get()) {
                    try {
                        ds.receive(dgram)
                        val msg = String(dgram.data, 0, dgram.length).trim()
                        Log.d(TAG, "UDP beacon: $msg")
                        // Accept any saathi beacon — token check skipped for demo reliability
                        if (msg.startsWith("saathi-", ignoreCase = true) && msg.contains(":")) {
                            val port = msg.substringAfterLast(":").trim().toIntOrNull() ?: continue
                            val host = dgram.address.hostAddress ?: continue
                            stopListening()
                            _state.value = _state.value.copy(status = SyncStatus.CONNECTING, statusMessage = "Connecting…")
                            receiveFromServer(host, port)
                            return@Thread
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.w(TAG, "Discovery timed out"); break
                    }
                }
                if (_state.value.status == SyncStatus.DISCOVERING) {
                    _state.value = _state.value.copy(
                        status        = SyncStatus.ERROR,
                        statusMessage = "Couldn't find patient — make sure both are on the same Wi-Fi",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discover error: ${e.message}")
                _state.value = _state.value.copy(status = SyncStatus.ERROR, statusMessage = "Couldn't sync")
            } finally {
                multicastLock?.release()
                isDiscovering.set(false)
            }
        }.start()
    }

    fun stopAll() {
        stopBeacon()
        stopListening()
        serverSocket?.runCatching { close() }
        serverSocket = null
        isAdvertising.set(false)
        isDiscovering.set(false)
        _state.value = NearbyState(status = SyncStatus.IDLE, statusMessage = "")
    }

    // ── Patient side helpers ────────────────────────────────────────────────────

    private fun beacon(wm: WifiManager, tcpPort: Int) {
        try {
            val ds = DatagramSocket()
            ds.broadcast = true
            beaconSocket = ds
            val msg      = "saathi-${pairedToken.lowercase()}:$tcpPort".toByteArray()

            // Compute subnet broadcast address for reliable same-WiFi delivery
            val bcastAddrs = mutableListOf<InetAddress>()
            try {
                val dhcp = wm.dhcpInfo
                val ipInt = dhcp.ipAddress
                val maskInt = dhcp.netmask
                if (maskInt != 0 && ipInt != 0) {
                    val bcastInt = (ipInt and maskInt) or maskInt.inv()
                    // Convert little-endian int to InetAddress
                    val bcastBytes = ByteArray(4) { i -> (bcastInt shr (i * 8)).toByte() }
                    bcastAddrs.add(InetAddress.getByAddress(bcastBytes))
                    Log.d(TAG, "Subnet broadcast: ${InetAddress.getByAddress(bcastBytes).hostAddress}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not compute subnet broadcast: ${e.message}")
            }
            // Always also send to 255.255.255.255 as fallback
            bcastAddrs.add(InetAddress.getByName("255.255.255.255"))

            Log.d(TAG, "Beaconing saathi-${pairedToken.lowercase()}:$tcpPort on UDP $BEACON_PORT")
            while (!ds.isClosed) {
                for (addr in bcastAddrs) {
                    try { ds.send(DatagramPacket(msg, msg.size, addr, BEACON_PORT)) }
                    catch (_: Exception) {}
                }
                Thread.sleep(BEACON_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Beacon stopped: ${e.message}")
        }
    }

    private fun stopBeacon() {
        beaconSocket?.runCatching { close() }
        beaconSocket = null
    }

    // ── Gynac side helpers ──────────────────────────────────────────────────────

    private fun stopListening() {
        listenSocket?.runCatching { close() }
        listenSocket = null
    }

    private fun receiveFromServer(host: String, port: Int) {
        try {
            Socket(host, port).use { socket ->
                socket.soTimeout = 15_000
                val json   = BufferedReader(InputStreamReader(socket.getInputStream())).readLine() ?: return
                val packet = SyncPacket.fromJson(json) ?: run { Log.w(TAG, "Could not parse packet"); return }
                _state.value = _state.value.copy(
                    status        = SyncStatus.SYNCED,
                    statusMessage = "Synced just now",
                    lastSyncAt    = System.currentTimeMillis(),
                    lastPacket    = packet,
                )
                onPacketReceived?.invoke(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
            _state.value = _state.value.copy(status = SyncStatus.ERROR, statusMessage = "Couldn't sync")
        }
    }
}

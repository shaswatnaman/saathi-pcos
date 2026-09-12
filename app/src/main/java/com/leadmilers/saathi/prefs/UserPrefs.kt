package com.leadmilers.saathi.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

enum class UserRole { SELF, COMPANION, NONE }

class UserPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("saathi_user_prefs", Context.MODE_PRIVATE)

    // ── Onboarding ────────────────────────────────────────────────────────────
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(v) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, v) }

    var role: UserRole
        get() = UserRole.valueOf(prefs.getString(KEY_ROLE, UserRole.NONE.name)!!)
        set(v) = prefs.edit { putString(KEY_ROLE, v.name) }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(v) = prefs.edit { putString(KEY_USER_NAME, v) }

    var partnerName: String
        get() = prefs.getString(KEY_PARTNER_NAME, "") ?: ""
        set(v) = prefs.edit { putString(KEY_PARTNER_NAME, v) }

    // ── Cycle onboarding ──────────────────────────────────────────────────────
    var lastPeriodDate: Long
        get() = prefs.getLong(KEY_LAST_PERIOD, 0L)
        set(v) = prefs.edit { putLong(KEY_LAST_PERIOD, v) }

    var cycleLength: Int
        get() = prefs.getInt(KEY_CYCLE_LENGTH, 28)
        set(v) = prefs.edit { putInt(KEY_CYCLE_LENGTH, v) }

    // Goals (comma-separated strings)
    var goals: Set<String>
        get() = prefs.getStringSet(KEY_GOALS, emptySet()) ?: emptySet()
        set(v) = prefs.edit { putStringSet(KEY_GOALS, v) }

    // ── Device identity ───────────────────────────────────────────────────────
    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            val new = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_DEVICE_ID, new) }
            return new
        }

    // ── Pairing ───────────────────────────────────────────────────────────────
    // Token that the primary user generates and shares as QR / code
    var pairingToken: String
        get() = prefs.getString(KEY_PAIRING_TOKEN, "") ?: ""
        set(v) = prefs.edit { putString(KEY_PAIRING_TOKEN, v) }

    // The paired remote device's ID (stored on both sides after pairing)
    var pairedDeviceId: String
        get() = prefs.getString(KEY_PAIRED_DEVICE_ID, "") ?: ""
        set(v) = prefs.edit { putString(KEY_PAIRED_DEVICE_ID, v) }

    var isPaired: Boolean
        get() = prefs.getBoolean(KEY_IS_PAIRED, false)
        set(v) = prefs.edit { putBoolean(KEY_IS_PAIRED, v) }

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(v) = prefs.edit { putLong(KEY_LAST_SYNC, v) }

    // ── Sharing permissions (primary user controls) ───────────────────────────
    var shareCycle: Boolean
        get() = prefs.getBoolean(SHARE_CYCLE, true)
        set(v) = prefs.edit { putBoolean(SHARE_CYCLE, v) }

    var sharePeriod: Boolean
        get() = prefs.getBoolean(SHARE_PERIOD, true)
        set(v) = prefs.edit { putBoolean(SHARE_PERIOD, v) }

    var shareSymptoms: Boolean
        get() = prefs.getBoolean(SHARE_SYMPTOMS, true)
        set(v) = prefs.edit { putBoolean(SHARE_SYMPTOMS, v) }

    var shareMood: Boolean
        get() = prefs.getBoolean(SHARE_MOOD, true)
        set(v) = prefs.edit { putBoolean(SHARE_MOOD, v) }

    var shareEnergy: Boolean
        get() = prefs.getBoolean(SHARE_ENERGY, true)
        set(v) = prefs.edit { putBoolean(SHARE_ENERGY, v) }

    var shareInsights: Boolean
        get() = prefs.getBoolean(SHARE_INSIGHTS, false)
        set(v) = prefs.edit { putBoolean(SHARE_INSIGHTS, v) }

    var sharePrivateNotes: Boolean
        get() = prefs.getBoolean(SHARE_PRIVATE_NOTES, false)
        set(v) = prefs.edit { putBoolean(SHARE_PRIVATE_NOTES, v) }

    // ── Companion access — companion-side stores what is shared with them ─────
    // Stored as JSON blob on companion device
    var lastSharedSnapshot: String
        get() = prefs.getString(KEY_SHARED_SNAPSHOT, "") ?: ""
        set(v) = prefs.edit { putString(KEY_SHARED_SNAPSHOT, v) }

    fun reset() = prefs.edit { clear() }

    companion object {
        private const val KEY_ONBOARDING_DONE   = "onboarding_done"
        private const val KEY_ROLE              = "user_role"
        private const val KEY_USER_NAME         = "user_name"
        private const val KEY_PARTNER_NAME      = "partner_name"
        private const val KEY_LAST_PERIOD       = "last_period_date"
        private const val KEY_CYCLE_LENGTH      = "cycle_length"
        private const val KEY_GOALS             = "goals"
        private const val KEY_DEVICE_ID         = "device_id"
        private const val KEY_PAIRING_TOKEN     = "pairing_token"
        private const val KEY_PAIRED_DEVICE_ID  = "paired_device_id"
        private const val KEY_IS_PAIRED         = "is_paired"
        private const val KEY_LAST_SYNC         = "last_sync_at"
        private const val KEY_SHARED_SNAPSHOT   = "last_shared_snapshot"

        private const val SHARE_CYCLE           = "share_cycle"
        private const val SHARE_PERIOD          = "share_period"
        private const val SHARE_SYMPTOMS        = "share_symptoms"
        private const val SHARE_MOOD            = "share_mood"
        private const val SHARE_ENERGY          = "share_energy"
        private const val SHARE_INSIGHTS        = "share_insights"
        private const val SHARE_PRIVATE_NOTES   = "share_private_notes"

        @Volatile private var INSTANCE: UserPrefs? = null
        fun getInstance(context: Context): UserPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPrefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}

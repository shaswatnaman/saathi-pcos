package com.leadmilers.saathi.sensor

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

object HealthConnectHelper {

    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun getClient(context: Context): HealthConnectClient? = try {
        if (isAvailable(context)) HealthConnectClient.getOrCreate(context) else null
    } catch (_: Exception) { null }

    /**
     * Returns today's total step count, or null if unavailable / permission denied.
     * Must be called from a coroutine.
     */
    suspend fun readTodaySteps(client: HealthConnectClient): Long? = try {
        val today   = LocalDate.now()
        val start   = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end     = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val request = ReadRecordsRequest(
            recordType      = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = client.readRecords(request)
        response.records.sumOf { it.count }
    } catch (_: Exception) { null }
}

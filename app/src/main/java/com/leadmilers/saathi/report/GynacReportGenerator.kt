package com.leadmilers.saathi.report

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.leadmilers.saathi.gynac.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object GynacReportGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val HEADER_H = 50f
    private const val FOOTER_H = 30f
    private const val CONTENT_TOP = MARGIN + HEADER_H
    private const val CONTENT_BOTTOM = PAGE_H - MARGIN - FOOTER_H

    private val cPlum   = Color.parseColor("#6A1B9A")
    private val cCoral  = Color.parseColor("#E8614A")
    private val cSage   = Color.parseColor("#4A8B7F")
    private val cAmber  = Color.parseColor("#FF8F00")
    private val cRed    = Color.parseColor("#D32F2F")
    private val cGreen  = Color.parseColor("#388E3C")
    private val cGray   = Color.parseColor("#757575")
    private val cText   = Color.parseColor("#212121")
    private val cBg     = Color.parseColor("#F3E5F5")
    private val cBgSage = Color.parseColor("#E0F2F1")

    suspend fun generate(context: Context, patient: GynacPatient, doctorName: String): String =
        withContext(Dispatchers.IO) {
            val dateStr  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val safeName = patient.name.replace(Regex("[^A-Za-z0-9]"), "_")
            val fileName = "Saathi_Clinical_${safeName}_$dateStr.pdf"
            val doc = PdfDocument()

            drawCoverPage(doc, patient, doctorName, dateStr)
            drawTimelinePage(doc, patient)
            drawMedNotesPage(doc, patient, doctorName)

            val path = saveDocument(context, doc, fileName)
            doc.close()
            path
        }

    // ── Page 1: Patient summary + risk + alerts ──────────────────────────────

    private fun drawCoverPage(doc: PdfDocument, p: GynacPatient, doctor: String, date: String) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas
        drawHeader(c, doctor)
        drawFooter(c, 1)

        var y = CONTENT_TOP + 16f

        // Patient identity block
        val statusColor = statusColor(p.status)
        c.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + 96f), 10f, 10f,
            Paint().apply { color = cBg })
        c.drawText(p.name, MARGIN + 14f, y + 30f, tp(20f, bold = true))
        c.drawText(
            "${p.age}y  ·  ${p.diagnosis}  ·  ${p.patientCode}",
            MARGIN + 14f, y + 56f, tp(13f, color = cGray),
        )
        c.drawText("Report: $date", MARGIN + 14f, y + 78f, tp(11f, color = cGray))

        // Status badge (top-right of block)
        val badge = statusLabel(p.status)
        val badgePaint = Paint().apply { color = statusColor }
        c.drawRoundRect(RectF(PAGE_W - MARGIN - 110f, y + 18f, PAGE_W - MARGIN - 10f, y + 50f),
            8f, 8f, badgePaint)
        c.drawText(badge, PAGE_W - MARGIN - 108f, y + 40f,
            tp(14f, bold = true, color = Color.WHITE))
        y += 114f

        // Last visit / next follow-up
        c.drawText("Last visit: ${p.lastVisitDays} days ago", MARGIN, y, tp(12f))
        val nf = p.nextFollowUpDays
        val nfText = when {
            nf == null  -> "No follow-up scheduled"
            nf <= 0     -> "Follow-up OVERDUE"
            nf <= 3     -> "Follow-up in $nf days — URGENT"
            else        -> "Follow-up in $nf days"
        }
        c.drawText(nfText, MARGIN + 220f, y, tp(12f, bold = nf != null && nf <= 3,
            color = if (nf != null && nf <= 3) cRed else cText))
        y += 28f

        // Cycle metrics row
        val metricBoxW = (PAGE_W - MARGIN * 2 - 20f) / 3f
        listOf(
            Triple("Avg cycle", "${p.avgCycleLength}d", trendColor(p.cycleTrend)),
            Triple("Recent",    "${p.recentCycleLength}d", trendColor(p.cycleTrend)),
            Triple("Sleep avg", "${p.sleepAvg}h", trendColor(p.sleepTrend)),
        ).forEachIndexed { i, (lbl, val_, col) ->
            val bx = MARGIN + i * (metricBoxW + 10f)
            c.drawRoundRect(RectF(bx, y, bx + metricBoxW, y + 60f), 8f, 8f,
                Paint().apply { color = col.withAlpha(30) })
            c.drawText(val_, bx + 10f, y + 32f, tp(20f, bold = true, color = col))
            c.drawText(lbl,  bx + 10f, y + 52f, tp(10f, color = cGray))
        }
        y += 78f

        // Alerts
        if (p.alerts.isNotEmpty()) {
            c.drawText("Active Alerts", MARGIN, y, tp(15f, bold = true))
            y += 22f
            for (alert in p.alerts) {
                if (y > CONTENT_BOTTOM - 20f) break
                val ac = alertColor(alert.severity)
                val icon = when (alert.severity) { AlertSev.URGENT -> "⚠" ; AlertSev.WARNING -> "▲" ; else -> "●" }
                c.drawText("$icon  ${alert.message}", MARGIN + 6f, y, tp(11f, color = ac))
                if (alert.daysAgo > 0) c.drawText("${alert.daysAgo}d ago", PAGE_W - MARGIN - 50f, y, tp(10f, color = cGray))
                y += 20f
            }
            y += 8f
        }

        // Score breakdown mini-bars
        c.drawText("Signal Summary", MARGIN, y, tp(15f, bold = true))
        y += 22f
        val barW = PAGE_W - MARGIN * 2 - 120f
        listOf(
            Triple("Cycle irregularity", p.cycleTrend.ordinal.toFloat() / 3f, cCoral),
            Triple("Acne / skin",        if (p.acneSeverity == "Severe") 1f else if (p.acneSeverity == "Moderate") 0.6f else 0.3f, cAmber),
            Triple("Fatigue",            (p.fatigueAvg - 1f) / 4f, cPlum),
            Triple("Sleep deficit",      ((8f - p.sleepAvg) / 4f).coerceIn(0f, 1f), cSage),
        ).forEach { (lbl, frac, col) ->
            if (y > CONTENT_BOTTOM - 20f) return@forEach
            val fill = (frac * barW).coerceAtLeast(4f)
            c.drawRoundRect(RectF(MARGIN + 120f, y - 11f, MARGIN + 120f + barW, y + 5f),
                3f, 3f, Paint().apply { color = Color.LTGRAY })
            c.drawRoundRect(RectF(MARGIN + 120f, y - 11f, MARGIN + 120f + fill, y + 5f),
                3f, 3f, Paint().apply { color = col })
            c.drawText(lbl, MARGIN, y, tp(11f))
            y += 26f
        }

        // Data completeness
        y += 6f
        c.drawText("Data completeness: ${p.dataCompleteness}%  ·  Last update: ${p.lastUpdateHours}h ago",
            MARGIN, y, tp(10f, color = cGray))

        doc.finishPage(page)
    }

    // ── Page 2: Timeline charts (cycle + acne + sleep) ───────────────────────

    private fun drawTimelinePage(doc: PdfDocument, p: GynacPatient) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create())
        val c = page.canvas
        drawHeader(c, p.name)
        drawFooter(c, 2)

        var y = CONTENT_TOP + 16f
        c.drawText("30-Day Trends — ${p.name}", MARGIN, y, tp(17f, bold = true))
        y += 30f

        // Cycle length history bar chart
        c.drawText("Cycle Length (days)", MARGIN, y, tp(13f, bold = true, color = cPlum))
        y += 16f
        drawBarChart(c, y, p.cycleHistory.map { it.toFloat() }, 20f, 50f, cPlum,
            listOf(28f to "Normal"), height = 80f)
        y += 96f

        // Sleep history line
        if (p.sleepHistory.isNotEmpty()) {
            c.drawText("Sleep (hours)", MARGIN, y, tp(13f, bold = true, color = cSage))
            y += 16f
            drawLineChart(c, y, p.sleepHistory.takeLast(30), 3f, 10f, cSage,
                listOf(7f to "Target"), height = 80f)
            y += 96f
        }

        // Acne severity history
        if (p.acneHistory.isNotEmpty()) {
            c.drawText("Acne Score (0–1)", MARGIN, y, tp(13f, bold = true, color = cCoral))
            y += 16f
            drawLineChart(c, y, p.acneHistory.takeLast(30), 0f, 1f, cCoral,
                listOf(0.5f to "Moderate"), height = 80f)
            y += 96f
        }

        // Trend summary text
        c.drawText("Trend Summary", MARGIN, y, tp(14f, bold = true))
        y += 20f
        listOf(
            "Cycle" to trendText(p.cycleTrend),
            "Acne"  to trendText(p.acneTrend),
            "Sleep" to trendText(p.sleepTrend),
            "Fatigue" to "${p.fatigueAvg.let { "%.1f".format(it) }}/5  —  ${trendText(p.fatigueTrend)}",
        ).forEach { (k, v) ->
            if (y > CONTENT_BOTTOM - 18f) return@forEach
            c.drawText("$k:", MARGIN, y, tp(11f, bold = true))
            c.drawText(v, MARGIN + 80f, y, tp(11f))
            y += 18f
        }

        doc.finishPage(page)
    }

    // ── Page 3: Medications + clinical notes ─────────────────────────────────

    private fun drawMedNotesPage(doc: PdfDocument, p: GynacPatient, doctor: String) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 3).create())
        val c = page.canvas
        drawHeader(c, p.name)
        drawFooter(c, 3)

        var y = CONTENT_TOP + 16f
        c.drawText("Medications & Clinical Notes", MARGIN, y, tp(17f, bold = true))
        y += 30f

        // Medications
        c.drawText("Current Medications", MARGIN, y, tp(14f, bold = true, color = cPlum))
        y += 18f
        val meds = p.medications.sortedByDescending { it.active }
        meds.forEach { med ->
            if (y > CONTENT_BOTTOM - 20f) return@forEach
            val col = if (med.active) cGreen else cGray
            val actText = if (med.active) "ACTIVE" else "PAST"
            c.drawRoundRect(RectF(MARGIN, y - 13f, MARGIN + 44f, y + 5f), 4f, 4f,
                Paint().apply { color = col })
            c.drawText(actText, MARGIN + 4f, y, tp(9f, bold = true, color = Color.WHITE))
            c.drawText("${med.name} ${med.dose}", MARGIN + 52f, y, tp(12f, bold = true))
            c.drawText("${med.frequency}  ·  from ${med.startDate}${med.endDate?.let { " → $it" } ?: ""}",
                MARGIN + 52f, y + 14f, tp(10f, color = cGray))
            if (med.notes.isNotBlank()) {
                c.drawText("Note: ${med.notes}", MARGIN + 52f, y + 26f, tp(9f, color = cGray))
            }
            y += if (med.notes.isNotBlank()) 46f else 32f
        }
        y += 8f

        // Clinical notes
        if (y < CONTENT_BOTTOM - 80f) {
            c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            y += 16f
            c.drawText("Clinical Notes", MARGIN, y, tp(14f, bold = true, color = cPlum))
            y += 20f
            p.clinicalNotes.forEach { note ->
                if (y > CONTENT_BOTTOM - 20f) return@forEach
                val noteColor = when (note.type) {
                    NoteType.CONSULTATION -> cPlum
                    NoteType.ASSESSMENT   -> cSage
                    NoteType.PLAN         -> cCoral
                    NoteType.PATIENT_NOTE -> cGray
                    NoteType.FOLLOWUP     -> cAmber
                }
                val typeLabel = note.type.name.lowercase().replaceFirstChar { it.uppercase() }
                    .replace("_", " ")
                c.drawRoundRect(RectF(MARGIN, y - 13f, MARGIN + 80f, y + 5f), 4f, 4f,
                    Paint().apply { color = noteColor.withAlpha(40) })
                c.drawText(typeLabel, MARGIN + 4f, y, tp(9f, color = noteColor))
                c.drawText(note.date, MARGIN + 90f, y, tp(10f, color = cGray))
                c.drawText("(${note.source.name.lowercase()})", PAGE_W - MARGIN - 60f, y, tp(9f, color = cGray))
                y += 16f

                // Wrap long note text at ~75 chars per line
                val words = note.content.split(" ")
                var line = ""
                for (word in words) {
                    if (y > CONTENT_BOTTOM - 14f) break
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (candidate.length > 80) {
                        c.drawText(line, MARGIN + 8f, y, tp(10f))
                        y += 14f
                        line = word
                    } else {
                        line = candidate
                    }
                }
                if (line.isNotEmpty() && y <= CONTENT_BOTTOM - 14f) {
                    c.drawText(line, MARGIN + 8f, y, tp(10f))
                    y += 14f
                }
                y += 10f
            }
        }

        // Annotation space at bottom
        if (y < CONTENT_BOTTOM - 80f) {
            y = maxOf(y, CONTENT_BOTTOM - 90f)
            c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            y += 14f
            c.drawText("Doctor's annotation space:", MARGIN, y, tp(11f, color = cGray))
            y += 14f
            repeat(3) {
                if (y <= CONTENT_BOTTOM - 16f) {
                    c.drawLine(MARGIN, y + 4f, PAGE_W - MARGIN, y + 4f,
                        Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f })
                    y += 18f
                }
            }
        }

        doc.finishPage(page)
    }

    // ── Mini chart helpers ────────────────────────────────────────────────────

    private fun drawBarChart(
        c: Canvas, top: Float, values: List<Float>,
        minVal: Float, maxVal: Float, color: Int,
        annotations: List<Pair<Float, String>>, height: Float,
    ) {
        if (values.isEmpty()) return
        val range = (maxVal - minVal).coerceAtLeast(0.01f)
        val chartW = PAGE_W - MARGIN * 2
        val barW = (chartW / values.size) * 0.7f
        val gap  = chartW / values.size

        // annotation lines
        annotations.forEach { (av, lbl) ->
            val ay = top + height - ((av - minVal) / range) * height
            c.drawLine(MARGIN, ay, PAGE_W - MARGIN, ay, Paint().apply {
                this.color = color; alpha = 80; strokeWidth = 1f
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            })
            c.drawText(lbl, PAGE_W - MARGIN - 30f, ay - 3f, tp(8f, color = color))
        }

        values.forEachIndexed { i, v ->
            val barH = ((v - minVal) / range * height).coerceAtLeast(2f)
            val bx = MARGIN + i * gap + (gap - barW) / 2f
            val bTop = top + height - barH
            c.drawRoundRect(RectF(bx, bTop, bx + barW, top + height), 2f, 2f,
                Paint().apply { this.color = color; alpha = 200 })
        }
        // x-axis
        c.drawLine(MARGIN, top + height, PAGE_W - MARGIN, top + height,
            Paint().apply { this.color = Color.LTGRAY; strokeWidth = 1f })
        // avg label
        val avg = values.average().toFloat()
        c.drawText("avg ${avg.toInt()}d", MARGIN, top + height + 12f, tp(9f, color = cGray))
    }

    private fun drawLineChart(
        c: Canvas, top: Float, values: List<Float>,
        minVal: Float, maxVal: Float, color: Int,
        annotations: List<Pair<Float, String>>, height: Float,
    ) {
        if (values.size < 2) return
        val range = (maxVal - minVal).coerceAtLeast(0.01f)
        val chartW = PAGE_W - MARGIN * 2
        val step = chartW / (values.size - 1)

        fun xOf(i: Int) = MARGIN + i * step
        fun yOf(v: Float) = top + height - ((v - minVal) / range * height * 0.9f) - height * 0.05f

        // Fill area
        val path = Path().apply {
            moveTo(xOf(0), top + height)
            lineTo(xOf(0), yOf(values[0]))
            for (i in 1 until values.size) {
                val cx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i]))
            }
            lineTo(xOf(values.size - 1), top + height)
            close()
        }
        c.drawPath(path, Paint().apply { this.color = color; alpha = 30 })

        // Annotation lines
        annotations.forEach { (av, lbl) ->
            val ay = yOf(av)
            c.drawLine(MARGIN, ay, PAGE_W - MARGIN, ay, Paint().apply {
                this.color = color; alpha = 80; strokeWidth = 1f
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            })
            c.drawText(lbl, PAGE_W - MARGIN - 30f, ay - 3f, tp(8f, color = color))
        }

        // Line
        val linePath = Path().apply {
            moveTo(xOf(0), yOf(values[0]))
            for (i in 1 until values.size) {
                val cx = (xOf(i - 1) + xOf(i)) / 2
                cubicTo(cx, yOf(values[i - 1]), cx, yOf(values[i]), xOf(i), yOf(values[i]))
            }
        }
        c.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; strokeWidth = 2f; style = Paint.Style.STROKE
        })

        // Endpoints
        c.drawCircle(xOf(0), yOf(values.first()), 3f, Paint().apply { this.color = color })
        c.drawCircle(xOf(values.size - 1), yOf(values.last()), 4f, Paint().apply { this.color = color })

        c.drawLine(MARGIN, top + height, PAGE_W - MARGIN, top + height,
            Paint().apply { this.color = Color.LTGRAY; strokeWidth = 1f })
        c.drawText("avg ${"%.1f".format(values.average())}", MARGIN, top + height + 12f, tp(9f, color = cGray))
    }

    // ── Shared layout ─────────────────────────────────────────────────────────

    private fun drawHeader(c: Canvas, subtitle: String) {
        c.drawRect(RectF(0f, 0f, PAGE_W.toFloat(), 5f), Paint().apply { color = cPlum })
        c.drawText("Saathi — Clinical Patient Report",
            MARGIN, MARGIN + 22f, tp(13f, bold = true, color = cPlum))
        c.drawText(subtitle, PAGE_W - MARGIN - subtitle.length * 6.5f, MARGIN + 22f, tp(11f, color = cGray))
        c.drawLine(MARGIN, MARGIN + 34f, PAGE_W - MARGIN, MARGIN + 34f,
            Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
    }

    private fun drawFooter(c: Canvas, page: Int) {
        c.drawLine(MARGIN, PAGE_H - MARGIN - 22f, PAGE_W - MARGIN, PAGE_H - MARGIN - 22f,
            Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        c.drawText("Confidential — For clinical use only. Not a standalone diagnostic report. Page $page of 3",
            MARGIN, PAGE_H - MARGIN - 6f, tp(9f, color = cGray))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun tp(size: Float, bold: Boolean = false, color: Int = cText) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
        }

    private fun Int.withAlpha(a: Int): Int =
        Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))

    private fun statusColor(s: GynacStatus) = when (s) {
        GynacStatus.STABLE       -> cGreen
        GynacStatus.MONITORING   -> cSage
        GynacStatus.FOLLOW_UP    -> cAmber
        GynacStatus.NEEDS_REVIEW -> cRed
    }

    private fun statusLabel(s: GynacStatus) = when (s) {
        GynacStatus.STABLE       -> "STABLE"
        GynacStatus.MONITORING   -> "MONITORING"
        GynacStatus.FOLLOW_UP    -> "FOLLOW-UP"
        GynacStatus.NEEDS_REVIEW -> "NEEDS REVIEW"
    }

    private fun trendColor(t: HealthTrend) = when (t) {
        HealthTrend.IMPROVING -> cGreen
        HealthTrend.WORSENING -> cRed
        HealthTrend.STABLE    -> cGray
        HealthTrend.VARIABLE  -> cAmber
    }

    private fun trendText(t: HealthTrend) = when (t) {
        HealthTrend.IMPROVING -> "Improving"
        HealthTrend.WORSENING -> "Worsening"
        HealthTrend.STABLE    -> "Stable"
        HealthTrend.VARIABLE  -> "Variable"
    }

    private fun alertColor(s: AlertSev) = when (s) {
        AlertSev.URGENT  -> cRed
        AlertSev.WARNING -> cAmber
        AlertSev.INFO    -> cSage
    }

    private fun saveDocument(context: Context, doc: PdfDocument, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { doc.writeTo(it) }
            uri.toString()
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName,
            )
            FileOutputStream(file).use { doc.writeTo(it) }
            file.absolutePath
        }
    }
}

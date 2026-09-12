package com.leadmilers.saathi.companion

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

object QrHelper {

    /** Generate a secure pairing token (6-char alphanumeric, upper-case). */
    fun generateToken(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // unambiguous chars
        return (1..6).map { chars.random() }.joinToString("")
    }

    /** Encode the primary device's pairing payload into a QR bitmap. */
    fun generatePairingQr(deviceId: String, token: String, sizePx: Int = 512): Bitmap {
        val payload = JSONObject().apply {
            put("app",      "saathi")
            put("id",       deviceId)
            put("token",    token)
        }.toString()

        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val bits  = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    /** Parse a companion-scanned QR payload. Returns (deviceId, token) or null on error. */
    fun parsePairingPayload(json: String): Pair<String, String>? = runCatching {
        val o = JSONObject(json)
        if (o.optString("app") != "saathi") return null
        Pair(o.getString("id"), o.getString("token"))
    }.getOrNull()
}

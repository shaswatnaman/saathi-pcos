package com.leadmilers.saathi.office

import android.content.Context
import android.util.Log
import java.io.File
import java.lang.reflect.Method

/**
 * Thin bridge to the iQOO Office Kit SDK.
 *
 * The SDK ships as a system-level service on iQOO devices and is accessed via
 * reflection so the app compiles and runs on non-iQOO hardware without crashing.
 * Every public function returns true on success, false when the SDK is absent or
 * the call fails, and logs a warning in either case.
 */
object OfficeBridge {

    private const val TAG = "OfficeBridge"

    // iQOO Office Kit service class names (from public SDK documentation)
    private const val SERVICE_CLASS  = "com.vivo.officekit.OfficeKitManager"
    private const val MIRROR_METHOD  = "mirrorFileToPC"
    private const val CLIP_METHOD    = "syncClipboardToPC"
    private const val TRANSFER_METHOD = "transferFileToPc"

    /**
     * Lazy-resolved manager instance — null when the SDK is not present.
     */
    private var manager: Any? = null
    private var initialized = false

    private fun getManager(context: Context): Any? {
        if (initialized) return manager
        initialized = true
        return try {
            val clazz = Class.forName(SERVICE_CLASS)
            val getInstance: Method = clazz.getMethod("getInstance", Context::class.java)
            manager = getInstance.invoke(null, context.applicationContext)
            manager
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "iQOO Office Kit SDK not available on this device")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Office Kit: ${e.message}")
            null
        }
    }

    /**
     * Mirror [pdfPath] to the connected laptop screen via the screen-mirror API.
     * Shows the PDF on the PC display while the phone retains control.
     */
    fun mirrorReport(context: Context, pdfPath: String): Boolean {
        if (!File(pdfPath).exists()) {
            Log.w(TAG, "mirrorReport: file not found — $pdfPath")
            return false
        }
        return invoke(context, MIRROR_METHOD, String::class.java, pdfPath)
    }

    /**
     * Push [text] to the laptop clipboard so it can be pasted in any PC app.
     */
    fun syncClipboard(context: Context, text: String): Boolean {
        return invoke(context, CLIP_METHOD, String::class.java, text)
    }

    /**
     * Transfer [filePath] to the default download folder on the connected laptop.
     */
    fun transferFile(context: Context, filePath: String): Boolean {
        if (!File(filePath).exists()) {
            Log.w(TAG, "transferFile: file not found — $filePath")
            return false
        }
        return invoke(context, TRANSFER_METHOD, String::class.java, filePath)
    }

    /**
     * Returns true when an iQOO device with the Office Kit service is present.
     */
    fun isAvailable(context: Context): Boolean = getManager(context) != null

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun invoke(context: Context, methodName: String, vararg paramTypes: Class<*>, args: Array<Any?>): Boolean {
        val mgr = getManager(context) ?: return false
        return try {
            val method: Method = mgr.javaClass.getMethod(methodName, *paramTypes)
            val result = method.invoke(mgr, *args)
            // SDK returns Boolean or int; treat non-null / non-zero as success
            when (result) {
                is Boolean -> result
                is Int     -> result == 0
                null       -> true  // void return = fire-and-forget success
                else       -> true
            }
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "Method $methodName not found in Office Kit")
            false
        } catch (e: Exception) {
            Log.w(TAG, "$methodName failed: ${e.message}")
            false
        }
    }

    // Single-argument convenience overload used in the functions above
    private fun invoke(context: Context, methodName: String, paramType: Class<*>, arg: Any): Boolean =
        invoke(context, methodName, paramType, args = arrayOf(arg))
}

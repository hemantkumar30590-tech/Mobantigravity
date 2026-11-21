package com.mobantigravity.launcher

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LauncherActivity : Activity() {

    companion object {
        private const val PKG_X11 = "ai.antigem.x11"
        private const val CLS_X11 = "com.termux.x11.MainActivity"
        private const val PKG_ANTIGEM = "ai.antigem"
        private const val GEM_CMD = "gem"
        private const val STOP_CMD = "pkill -f 'antigravity|language_server|gem-watchdog|termux-x11' || true"
        private const val SETUP_CMD = "bash /sdcard/Download/MobAntigravity/setup.sh"
        private const val SYNC_CMD = "bash /sdcard/Download/MobAntigravity/sync-scripts.sh"
        private const val BASE_URL =
            "https://github.com/hemantkumar30590-tech/Mobantigravity/releases/download/latest-apk/"
        private const val URL_ANTIGEM = BASE_URL + "antigem.apk"
        private const val URL_X11 = BASE_URL + "x11.apk"
        private const val X11_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

        private const val PREFS_NAME = "mob_setup"
        private const val KEY_STATE = "setup_state"
        private const val KEY_PENDING_AUTOTYPE = "pending_autotype"
        private const val KEY_COMMAND = "autotype_command"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val STATE_NONE = "none"
        private const val STATE_WAIT_ANTIGEM = "wait_antigem"
        private const val STATE_WAIT_X11 = "wait_x11"
        private const val STATE_WAIT_ACCESSIBILITY = "wait_accessibility"
        private const val REQ_NOTIF_PERM = 1042
    }

    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private var bridgeHealthy: Boolean? = null

    data class DisplayProfile(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val scaleFactor: String,
        val xftDpi: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B1020")) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        scroll.addView(root)
        setContentView(scroll)

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val prefs = prefs()
        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            File(filesDir, "setup_complete").writeText("done")
            prefs.edit().putBoolean(KEY_SETUP_DONE, false).apply()
            toast("Setup complete")
        }
        if (isAccessibilityEnabled()) {
            GemKeepAliveService.start(applicationContext)
        }
        checkBridgeHealthAsync()
        render()
        continueSetupFlow()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF_PERM)
            }
        }
    }

    private fun computeDisplayProfile(): DisplayProfile {
        val dm = resources.displayMetrics
        var usableW = dm.widthPixels
        var usableH = dm.heightPixels

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()
            )
            usableW = (bounds.width() - insets.left - insets.right).coerceAtLeast(720)
            usableH = (bounds.height() - insets.top - insets.bottom).coerceAtLeast(1080)
        }

        val dpi = dm.densityDpi.coerceAtLeast(240)
        val scale = when {
            dpi >= 440 -> "2"
            dpi >= 340 -> "1.75"
            else -> "1.5"
        }
        val xft = when {
            dpi >= 440 -> 288
            dpi >= 340 -> 240
            else -> 192
        }
        return DisplayProfile(usableW, usableH, dpi, scale, xft)
    }

    private fun buildDisplayProfileEnv(profile: DisplayProfile): String {
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return buildString {
            appendLine("# Auto-generated by MobAntigravity Launcher v1.4.0")
            appendLine("MOB_DEVICE_MODEL="$deviceModel"")
            appendLine("MOB_DISPLAY_RES="${profile.widthPx}x${profile.heightPx}"")
            appendLine("MOB_DISPLAY_DPI=${profile.densityDpi}")
            appendLine("MOB_SCALE_FACTOR="${profile.scaleFactor}"")
            appendLine("MOB_XFT_DPI=${profile.xftDpi}")
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun checkBridgeHealthAsync() {
        Thread {
            val ok = try {
                val conn = URL("http://127.0.0.1:${GemAutoTypeService.BRIDGE_PORT}/health")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 400
                conn.readTimeout = 400
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                code == 200
            } catch (_: Exception) {
                false
            }
            if (bridgeHealthy != ok) {
                bridgeHealthy = ok
                runOnUiThread { render() }
            }
        }.start()
    }

    private fun prefs(): SharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun setState(state: String) {
        prefs().edit().putString(KEY_STATE, state).apply()
    }

    private fun render() {
        root.removeAllViews()
        val setupDone = File(filesDir, "setup_complete").exists()
        if (!isInstalled(PKG_ANTIGEM) || !isInstalled(PKG_X11) || !setupDone) {
            renderSetup()
        } else {
            renderLauncher()
        }
    }

    private fun renderStatusCard() {
        val profile = computeDisplayProfile()
        val accOn = isAccessibilityEnabled()
        val batOk = isBatteryOptimizationIgnored()
        val bridgeStatus = when (bridgeHealthy) {
            true -> "✓ Bridge 8898 Alive"
            false -> "✕ Bridge 8898 Offline"
            null -> "… Checking Bridge 8898"
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#131B33"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        }

        val summary = TextView(this).apply {
            text = buildString {
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                appendLine("Auto Display: ${profile.widthPx}x${profile.heightPx} • Scale ${profile.scaleFactor}x • Xft ${profile.xftDpi}")
                appendLine("${if (accOn) "✓" else "✕"} Accessibility   ${if (batOk) "✓" else "!"} Battery Unrestricted   $bridgeStatus")
            }.trim()
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
        }
        card.addView(summary)
        root.addView(card)
    }

    private fun renderColorOsHelperSection() {
        val batOk = isBatteryOptimizationIgnored()
        if (!batOk) {
            addButton(root, "Allow Background / Battery Unrestricted", "#D97706") {
                requestIgnoreBatteryOptimizations()
            }
            addHint("Recommended on Oppo / ColorOS so the keyboard bridge (port 8898) never drops.")
            addSpacer(10)
        }

        if (!isAccessibilityEnabled()) {
            addButton(root, "Android 13/14+ Restricted Settings (App Info)", "#475569") {
                openAppDetailsSettings()
            }
            addHint("If Accessibility toggle is greyed out: tap App Info → ⋮ (top-right) → Allow restricted settings, then enable MobAntigravity.")
            addSpacer(10)
        }
    }

    private fun renderSetup() {
        addTitle("MobAntigravity v1.4")
        addHint("One-time setup tuned for mobile & Oppo A79 5G (MediaTek Mali + ColorOS).")
        renderStatusCard()

        val state = prefs().getString(KEY_STATE, STATE_NONE) ?: STATE_NONE
        if (state == STATE_NONE) {
            addButton(root, "1-Tap Install & Setup", "#059669") { startOneTapSetup() }
            addHint(
                "Downloads both apps, opens install prompts, writes your exact screen profile & scripts, " +
                    "then auto-types the setup command into AntiGem."
            )
        } else {
            addHint("Setup is in progress: ${state.replace('_', ' ')}")
            addButton(root, "Continue / Retry setup", "#B45309") {
                setState(STATE_NONE)
                startOneTapSetup()
            }
            addHint("Return here after each Android install screen to continue automatically.")
        }

        addSpacer(16)
        renderColorOsHelperSection()

        val antiInstalled = isInstalled(PKG_ANTIGEM)
        val x11Installed = isInstalled(PKG_X11)

        if (antiInstalled && x11Installed) {
            addHint("✓ AntiGem installed
✓ Termux-X11 installed")
        } else {
            addHint("Manual fallback – install the two required apps if the one-tap flow stops.")
        }

        addSpacer(10)

        if (!antiInstalled) {
            addButton(root, "Install AntiGem", "#3B82F6") {
                downloadAndInstall("antigem.apk", URL_ANTIGEM)
            }
            addSpacer(8)
        }

        if (!x11Installed) {
            addButton(root, "Install Termux-X11", "#8B5CF6") {
                downloadAndInstall("x11.apk", URL_X11)
            }
            addSpacer(8)
        }

        addButton(root, "Copy setup command", "#0F766E") { prepareSetupFiles() }
        addHint("Copies: $SETUP_CMD")
        addSpacer(8)

        addButton(root, "Open AntiGem terminal", "#374151") { openAntiGem() }
        addHint("Use this only if auto-type is not available. Paste the command and press Enter.")
        addSpacer(8)

        addButton(root, "I finished setup", "#16A34A") {
            if (isInstalled(PKG_ANTIGEM) && isInstalled(PKG_X11)) {
                prefs().edit()
                    .putBoolean(KEY_PENDING_AUTOTYPE, false)
                    .putString(KEY_STATE, STATE_NONE)
                    .apply()
                File(filesDir, "setup_complete").writeText("done")
                toast("Setup marked complete")
                render()
            } else {
                toast("Install both apps first")
            }
        }

        addSpacer(20)
        addNote(
            "Android shows its own install confirmation for each APK. " +
                "Accessibility is used for one-time setup auto-type and the Zero-ADB Gboard bridge."
        )
    }

    private fun renderLauncher() {
        addTitle("MobAntigravity v1.4")
        addHint("Antigravity 2.0 for Android – mobile & Oppo A79 5G tuned launcher")
        renderStatusCard()
        renderColorOsHelperSection()

        addButton(root, "Open Antigravity GUI", "#3B82F6") { openX11() }
        addHint("Opens Termux-X11 directly. If Antigravity is running, the GUI appears immediately.")
        addSpacer(12)

        addButton(root, "Open AntiGem terminal", "#8B5CF6") {
            copyText("gem", GEM_CMD)
            openAntiGem()
        }
        addHint(""gem" is copied to clipboard. Paste & press Enter to start Antigravity.")
        addSpacer(12)

        addButton(root, "Fast Sync Scripts & Display Profile", "#0F766E") {
            prepareSyncFiles()
        }
        addHint("Updates gem, patch_va39.py, Mali/RAM tuning & dynamic screen resolution in 2 seconds without wiping Debian.")
        addSpacer(12)

        addButton(root, "Copy Stop Session Command", "#DC2626") {
            copyText("stop", STOP_CMD)
            toast("Stop command copied – paste in AntiGem to free RAM & battery")
        }
        addSpacer(12)

        addButton(root, "Re-run full setup", "#B45309") {
            File(filesDir, "setup_complete").delete()
            prefs().edit()
                .putString(KEY_STATE, STATE_NONE)
                .putBoolean(KEY_PENDING_AUTOTYPE, false)
                .apply()
            toast("Setup screen enabled")
            render()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                openAppDetailsSettings()
            }
        }
    }

    private fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            toast("Open Settings → Apps → MobAntigravity manually")
        }
    }

    private fun startOneTapSetup() {
        prefs().edit()
            .putString(KEY_STATE, STATE_WAIT_ANTIGEM)
            .putBoolean(KEY_PENDING_AUTOTYPE, true)
            .putString(KEY_COMMAND, SETUP_CMD)
            .apply()
        toast("Downloading installers...")
        Thread {
            try {
                val antiApk = downloadApk("antigem.apk", URL_ANTIGEM)
                val x11Apk = downloadApk("x11.apk", URL_X11)
                runOnUiThread { afterDownload(antiApk, x11Apk) }
            } catch (e: Exception) {
                runOnUiThread {
                    setState(STATE_NONE)
                    toast("Download failed: ${e.message ?: "unknown error"}")
                }
            }
        }.start()
    }

    private fun afterDownload(antiApk: File, x11Apk: File) {
        when {
            !isInstalled(PKG_ANTIGEM) -> {
                setState(STATE_WAIT_ANTIGEM)
                installApkFile(antiApk)
            }
            !isInstalled(PKG_X11) -> {
                setState(STATE_WAIT_X11)
                installApkFile(x11Apk)
            }
            else -> finishInstallPhase()
        }
    }

    private fun continueSetupFlow() {
        when (prefs().getString(KEY_STATE, STATE_NONE) ?: STATE_NONE) {
            STATE_NONE -> Unit

            STATE_WAIT_ANTIGEM -> {
                if (isInstalled(PKG_ANTIGEM)) {
                    if (!isInstalled(PKG_X11)) {
                        val apk = File(filesDir, "apks/x11.apk")
                        if (apk.exists()) {
                            setState(STATE_WAIT_X11)
                            installApkFile(apk)
                        } else {
                            startOneTapSetup()
                        }
                    } else {
                        finishInstallPhase()
                    }
                } else {
                    toast("Waiting for AntiGem install")
                }
            }

            STATE_WAIT_X11 -> {
                if (isInstalled(PKG_X11)) {
                    finishInstallPhase()
                } else {
                    toast("Waiting for Termux-X11 install")
                }
            }

            STATE_WAIT_ACCESSIBILITY -> {
                if (isAccessibilityEnabled()) {
                    setState(STATE_NONE)
                    openAntiGem()
                } else {
                    toast("Enable MobAntigravity in Accessibility, or paste the command manually")
                }
            }
        }
    }

    private fun finishInstallPhase() {
        prepareSetupFiles(announce = false)
        prefs().edit()
            .putBoolean(KEY_PENDING_AUTOTYPE, true)
            .putString(KEY_COMMAND, SETUP_CMD)
            .apply()

        if (isAccessibilityEnabled()) {
            setState(STATE_NONE)
            openAntiGem()
        } else {
            setState(STATE_WAIT_ACCESSIBILITY)
            toast("Enable MobAntigravity accessibility to auto-type the command")
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                toast("Could not open Accessibility settings")
                setState(STATE_NONE)
                openAntiGem()
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val service = GemAutoTypeService::class.java
        val component = ComponentName(this, service)
        val full = component.flattenToString()
        val short = "${packageName}/.${service.simpleName}"
        return Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            (enabled.contains(full, true) || enabled.contains(short, true) || enabled.contains(service.simpleName, true))
    }

    private fun addTitle(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        })
    }

    private fun addButton(root: LinearLayout, text: String, colorHex: String, click: View.OnClickListener) {
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(colorHex))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
            setOnClickListener(click)
            root.addView(this)
        }
    }

    private fun addHint(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#8B95B0"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        })
    }

    private fun addNote(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#6B7490"))
            textSize = 12f
        })
    }

    private fun addSpacer(dpSize: Int) {
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(dpSize)
            )
        })
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun downloadApk(fileName: String, url: String): File {
        val dir = File(filesDir, "apks").apply { mkdirs() }
        val apk = File(dir, fileName)
        URL(url).openStream().use { input ->
            FileOutputStream(apk).use { output -> input.copyTo(output) }
        }
        return apk
    }

    private fun downloadAndInstall(fileName: String, url: String) {
        toast("Downloading $fileName...")
        Thread {
            try {
                val apk = downloadApk(fileName, url)
                runOnUiThread { installApkFile(apk) }
            } catch (e: Exception) {
                runOnUiThread { toast("Download failed: ${e.message ?: "unknown error"}") }
            }
        }.start()
    }

    private fun installApkFile(apk: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("Install failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun extractAllAssetsToDownloads() {
        val scripts = assets.list("scripts")?.toList().orEmpty()
        scripts.forEach { name ->
            copyAssetToDownloads("scripts/$name", "scripts/$name")
            copyAssetToDownloads("scripts/$name", name)
        }
        copyAssetToDownloads("setup.sh", "setup.sh")
        copyAssetToDownloads("sync-scripts.sh", "sync-scripts.sh")

        val profileEnv = buildDisplayProfileEnv(computeDisplayProfile())
        writeTextToDownloads("display_profile.env", profileEnv)
    }

    private fun prepareSetupFiles(announce: Boolean = true) {
        try {
            extractAllAssetsToDownloads()
            copyText("setup", SETUP_CMD, announce)
            if (announce) toast("Setup command copied – paste it in AntiGem")
        } catch (e: Exception) {
            toast("Copy failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun prepareSyncFiles() {
        try {
            extractAllAssetsToDownloads()
            copyText("sync", SYNC_CMD, announce = false)
            if (isAccessibilityEnabled()) {
                prefs().edit()
                    .putBoolean(KEY_PENDING_AUTOTYPE, true)
                    .putString(KEY_COMMAND, SYNC_CMD)
                    .apply()
                toast("Scripts extracted – opening AntiGem to fast-sync")
                openAntiGem()
            } else {
                toast("Fast-sync command copied – paste in AntiGem")
                openAntiGem()
            }
        } catch (e: Exception) {
            toast("Sync prep failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun copyAssetToDownloads(assetPath: String, relPath: String) {
        val bytes = assets.open(assetPath).use { it.readBytes() }
        writeBytesToDownloads(relPath, bytes)
    }

    private fun writeTextToDownloads(relPath: String, content: String) {
        writeBytesToDownloads(relPath, content.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytesToDownloads(relPath: String, bytes: ByteArray) {
        val cleanRel = relPath.trimStart('/')
        val slashIdx = cleanRel.lastIndexOf('/')
        val subDir = if (slashIdx > 0) cleanRel.substring(0, slashIdx) else ""
        val fileName = if (slashIdx >= 0) cleanRel.substring(slashIdx + 1) else cleanRel

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val relativeDir = if (subDir.isEmpty()) {
                "${Environment.DIRECTORY_DOWNLOADS}/MobAntigravity/"
            } else {
                "${Environment.DIRECTORY_DOWNLOADS}/MobAntigravity/$subDir/"
            }
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                selection,
                arrayOf(fileName, relativeDir)
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create $cleanRel in Downloads")
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: throw IllegalStateException("Could not write $cleanRel")
        } else {
            val base = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MobAntigravity"
            )
            val targetDir = if (subDir.isEmpty()) base else File(base, subDir)
            targetDir.mkdirs()
            FileOutputStream(File(targetDir, fileName)).use { out ->
                out.write(bytes)
            }
        }
    }

    private fun copyText(label: String, text: String, announce: Boolean = true) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        if (announce) toast(""$text" copied to clipboard")
    }

    private fun openX11() {
        try {
            Intent().apply {
                component = ComponentName(PKG_X11, CLS_X11)
                addFlags(X11_FLAGS)
                startActivity(this)
            }
        } catch (_: Exception) {
            val launch = packageManager.getLaunchIntentForPackage(PKG_X11)
            if (launch != null) {
                launch.addFlags(X11_FLAGS)
                startActivity(launch)
            } else {
                toast("Termux-X11 app not installed")
            }
        }
    }

    private fun openAntiGem() {
        val launch = packageManager.getLaunchIntentForPackage(PKG_ANTIGEM)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } else {
            toast("AntiGem app not found")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
}

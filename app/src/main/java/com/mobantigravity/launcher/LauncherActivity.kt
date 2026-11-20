package com.mobantigravity.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class LauncherActivity : Activity() {

    companion object {
        private const val PKG_X11 = "ai.antigem.x11"
        private const val CLS_X11 = "com.termux.x11.MainActivity"
        private const val PKG_ANTIGEM = "ai.antigem"
        private const val GEM_CMD = "gem"
        private const val SETUP_CMD = "bash /sdcard/Download/MobAntigravity/setup.sh"
        private const val DOWNLOAD_DIR = "Download/MobAntigravity"
        private const val BASE_URL =
            "https://github.com/hemantkumar30590-tech/Mobantigravity/releases/download/latest-apk/"
        private const val URL_ANTIGEM = BASE_URL + "antigem.apk"
        private const val URL_X11 = BASE_URL + "x11.apk"
        private const val X11_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }

    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B1020")) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
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

    private fun renderSetup() {
        addTitle("MobAntigravity")
        addHint("One-time guided setup. Android will ask you to confirm each APK install.")

        addSpacer(20)

        val antiInstalled = isInstalled(PKG_ANTIGEM)
        val x11Installed = isInstalled(PKG_X11)

        if (antiInstalled && x11Installed) {
            addHint("✓ AntiGem installed\n✓ Termux-X11 installed")
        } else {
            addHint("Step 1/4: install the two required apps.")
        }

        addSpacer(10)

        if (!antiInstalled) {
            addButton(root, "1.  Install AntiGem", "#3B82F6") {
                downloadAndInstall("antigem.apk", URL_ANTIGEM)
            }
            addHint("Bundled APK. Tap Install / Allow unknown apps when Android asks.")
            addSpacer(10)
        }

        if (!x11Installed) {
            addButton(root, "2.  Install Termux-X11", "#8B5CF6") {
                downloadAndInstall("x11.apk", URL_X11)
            }
            addHint("Bundled APK. This provides the X11 window and Gboard bridge.")
            addSpacer(10)
        }

        addButton(root, "3.  Copy setup command", "#0F766E") { prepareSetupFiles() }
        addHint("Copies: $SETUP_CMD")
        addSpacer(10)

        addButton(root, "4.  Open AntiGem terminal", "#374151") { openAntiGem() }
        addHint("Paste the copied command once and press Enter. It installs PRoot, Debian and Antigravity core, then applies the mobile-tuned fixes.")
        addSpacer(10)

        addButton(root, "5.  I finished setup", "#16A34A") {
            if (isInstalled(PKG_ANTIGEM) && isInstalled(PKG_X11)) {
                File(filesDir, "setup_complete").writeText("done")
                toast("Setup marked complete")
                render()
            } else {
                toast("Install both apps first")
            }
        }

        addSpacer(24)
        addNote(
            "After this one-time setup, the launcher opens Antigravity directly. " +
                "Android does not allow silent installation of other apps, so the two install confirmations are unavoidable."
        )
    }

    private fun renderLauncher() {
        addTitle("MobAntigravity")
        addHint("Antigravity 2.0 for Android — mobile-tuned launcher")
        addSpacer(24)

        addButton(root, "Open Antigravity GUI", "#3B82F6") { openX11() }
        addHint("Termux-X11 window opens. If Antigravity is already running, the GUI appears here.")
        addSpacer(12)

        addButton(root, "Open AntiGem terminal", "#8B5CF6") {
            copyText("gem", GEM_CMD)
            openAntiGem()
        }
        addHint("\"gem\" is copied to the clipboard. Paste and press Enter if the GUI is not running.")
        addSpacer(12)

        addButton(root, "Copy \"gem\" command", "#374151") { copyText("gem", GEM_CMD) }
        addSpacer(16)

        addButton(root, "Re-run setup", "#B45309") {
            File(filesDir, "setup_complete").delete()
            toast("Setup screen enabled")
            render()
        }
    }

    private fun addTitle(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        })
    }

    private fun addButton(root: LinearLayout, text: String, colorHex: String, click: View.OnClickListener) {
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(colorHex))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
            setOnClickListener(click)
            root.addView(this)
        }
    }

    private fun addHint(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#7C86A3"))
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
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun downloadAndInstall(fileName: String, url: String) {
        toast("Downloading $fileName...")
        Thread {
            try {
                val dir = File(filesDir, "apks").apply { mkdirs() }
                val apk = File(dir, fileName)
                java.net.URL(url).openStream().use { input ->
                    FileOutputStream(apk).use { output -> input.copyTo(output) }
                }
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

    private fun prepareSetupFiles() {
        try {
            val scripts = assets.list("scripts")?.toList().orEmpty()
            scripts.forEach { name ->
                copyAssetToDownloads("scripts/$name", name)
            }
            copyAssetToDownloads("setup.sh", "setup.sh")
            copyText("setup", SETUP_CMD)
            toast("Setup command copied — paste it in AntiGem")
        } catch (e: Exception) {
            toast("Copy failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun copyAssetToDownloads(assetPath: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/MobAntigravity"
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                selection,
                arrayOf(name, "$relative/")
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create $name in Downloads")
            resolver.openOutputStream(uri)?.use { out ->
                assets.open(assetPath).use { it.copyTo(out) }
            } ?: throw IllegalStateException("Could not write $name")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MobAntigravity"
            ).apply { mkdirs() }
            assets.open(assetPath).use { input ->
                FileOutputStream(File(dir, name)).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun copyText(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("\"$text\" copied to clipboard")
    }

    private fun openX11() {
        try {
            Intent().apply {
                component = ComponentName(PKG_X11, CLS_X11)
                addFlags(X11_FLAGS)
                startActivity(this)
            }
        } catch (e: Exception) {
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

package com.mobantigravity.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LauncherActivity : Activity() {

    companion object {
        private const val PKG_X11 = "ai.antigem.x11"
        private const val CLS_X11 = "com.termux.x11.MainActivity"
        private const val PKG_ANTIGEM = "ai.antigem"
        private const val GEM_CMD = "gem"
        private const val X11_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(20)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1020"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "MobAntigravity"
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "Antigravity 2.0 for Android — mobile-tuned launcher"
            setTextColor(Color.parseColor("#9AA4BF"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        })

        addSpacer(root, 24)

        addButton(root, "1.  Open Antigravity GUI", "#3B82F6") { openX11() }
        addHint(root, "Termux-X11 window opens. If Antigravity is already running, the GUI appears here.")

        addSpacer(root, 12)

        addButton(root, "2.  Open AntiGem terminal", "#8B5CF6") {
            copyGem()
            openAntiGem()
        }
        addHint(root, "\"gem\" is copied to the clipboard. Paste it in the terminal and press Enter once.")

        addSpacer(root, 12)

        addButton(root, "Copy \"gem\" command", "#374151") { copyGem() }

        addSpacer(root, 24)

        root.addView(TextView(this).apply {
            text = "Tuned for 1080x2161 X11, scale factor 2, smart keyboard bridge and visible sidebar/top bar. " +
                "Android does not allow one app to run shell commands inside another app, so the first start " +
                "has to be typed or pasted once in the AntiGem terminal."
            setTextColor(Color.parseColor("#6B7490"))
            textSize = 12f
        })

        setContentView(scroll)
    }

    private fun addButton(
        root: LinearLayout,
        text: String,
        colorHex: String,
        click: View.OnClickListener
    ) {
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

    private fun addHint(root: LinearLayout, text: String) {
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#7C86A3"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            root.addView(this)
        }
    }

    private fun addSpacer(root: LinearLayout, dpSize: Int) {
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(dpSize)
            )
            root.addView(this)
        }
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
            toast("\"gem\" copied - paste and press Enter")
        } else {
            toast("AntiGem app not found")
        }
    }

    private fun copyGem() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("gem", GEM_CMD))
            toast("\"gem\" copied to clipboard")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
}

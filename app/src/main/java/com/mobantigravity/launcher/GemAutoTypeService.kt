package com.mobantigravity.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayDeque

class GemAutoTypeService : AccessibilityService() {

    companion object {
        const val PREFS_NAME = "mob_setup"
        const val KEY_PENDING_AUTOTYPE = "pending_autotype"
        const val KEY_COMMAND = "autotype_command"
        const val KEY_SETUP_DONE = "setup_done"
        const val BRIDGE_PORT = 8898
        private const val TARGET_PACKAGE = "ai.antigem"
        private const val X11_PACKAGE = "ai.antigem.x11"
        private const val SETUP_CMD = "bash /sdcard/Download/MobAntigravity/setup.sh"
        private const val ACTION_IME = "com.mobantigravity.launcher.ACTION_IME"
    }

    private var lastAttempt = 0L
    private var doneNotified = false
    @Volatile private var currentPackage: String = ""
    @Volatile private var isImeShown: Boolean = false
    @Volatile private var isX11Focused: Boolean = false
    @Volatile private var lastToggleTime: Long = 0L
    @Volatile private var serviceRunning: Boolean = false

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var imeReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRunning = true
        GemKeepAliveService.start(applicationContext)
        startLocalServer()
        registerImeReceiver()
        inspect()
    }

    override fun onDestroy() {
        serviceRunning = false
        super.onDestroy()
        stopLocalServer()
        unregisterImeReceiver()
        GemKeepAliveService.stop(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString() ?: ""
        if (pkg.isNotEmpty()) {
            currentPackage = pkg
            isX11Focused = (pkg == X11_PACKAGE)
        }
        checkImeState()
        if (pkg == TARGET_PACKAGE) {
            inspect()
        }
    }

    override fun onInterrupt() {
        doneNotified = false
    }

    private fun checkImeState(): Boolean {
        return try {
            val wins = windows
            val ime = wins != null && wins.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            isImeShown = ime
            ime
        } catch (e: Exception) {
            false
        }
    }

    private fun registerImeReceiver() {
        if (imeReceiver != null) return
        imeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.getStringExtra("action") ?: "toggle"
                handleImeAction(action)
            }
        }
        val filter = IntentFilter(ACTION_IME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(imeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(imeReceiver, filter)
        }
    }

    private fun unregisterImeReceiver() {
        imeReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            imeReceiver = null
        }
    }

    @Synchronized
    fun handleImeAction(action: String): Pair<String, String> {
        checkImeState()
        val now = System.currentTimeMillis()
        if (action.lowercase() != "state" && action.lowercase() != "health" && now - lastToggleTime < 450L) {
            return "200 OK" to "{\"ok\":true,\"debounced\":true,\"imeShown\":$isImeShown}"
        }

        return when (action.lowercase()) {
            "health" -> {
                "200 OK" to "{\"ok\":true,\"bridge\":\"alive\",\"port\":$BRIDGE_PORT,\"x11Focused\":$isX11Focused}"
            }
            "state" -> {
                "200 OK" to "{\"ok\":true,\"imeShown\":$isImeShown,\"x11Focused\":$isX11Focused,\"package\":\"$currentPackage\"}"
            }
            "on" -> {
                if (isX11Focused && !isImeShown) {
                    lastToggleTime = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    isImeShown = true
                    "200 OK" to "{\"ok\":true,\"action\":\"shown\",\"imeShown\":true}"
                } else {
                    "200 OK" to "{\"ok\":true,\"action\":\"none\",\"imeShown\":$isImeShown}"
                }
            }
            "off" -> {
                if (isImeShown) {
                    lastToggleTime = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    isImeShown = false
                    "200 OK" to "{\"ok\":true,\"action\":\"hidden\",\"imeShown\":false}"
                } else {
                    "200 OK" to "{\"ok\":true,\"action\":\"none\",\"imeShown\":$isImeShown}"
                }
            }
            "toggle", "back" -> {
                lastToggleTime = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                "200 OK" to "{\"ok\":true,\"action\":\"toggled\"}"
            }
            else -> {
                "400 Bad Request" to "{\"ok\":false,\"error\":\"unknown action $action\"}"
            }
        }
    }

    private fun startLocalServer() {
        if (serverThread?.isAlive == true) return
        serverThread = Thread {
            while (serviceRunning) {
                try {
                    val socket = ServerSocket()
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), BRIDGE_PORT), 20)
                    serverSocket = socket
                    while (serviceRunning && !socket.isClosed) {
                        val client = socket.accept()
                        handleClient(client)
                    }
                } catch (_: Exception) {
                    // Retry bind briefly if socket was interrupted by OEM power management
                    if (serviceRunning) {
                        try { Thread.sleep(1500L) } catch (_: InterruptedException) { break }
                    }
                } finally {
                    try { serverSocket?.close() } catch (_: Exception) {}
                    serverSocket = null
                }
            }
        }.apply {
            isDaemon = true
            name = "MobImeBridgeServer"
            start()
        }
    }

    private fun stopLocalServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
        serverThread?.interrupt()
        serverThread = null
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                client.soTimeout = 2000
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val line = reader.readLine() ?: return@Thread
                val parts = line.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"

                val action = when {
                    path.contains("/health") -> "health"
                    path.contains("/ime/state") -> "state"
                    path.contains("/ime/on") -> "on"
                    path.contains("/ime/off") -> "off"
                    path.contains("/ime/toggle") -> "toggle"
                    path.contains("/ime/back") -> "back"
                    else -> "unknown"
                }

                val (status, body) = if (action == "unknown") {
                    "404 Not Found" to "{\"ok\":false,\"error\":\"not found\"}"
                } else {
                    handleImeAction(action)
                }

                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val response = "HTTP/1.1 $status\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"

                val out = client.getOutputStream()
                out.write(response.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()
            } catch (_: Exception) {
                // Client error
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun inspect() {
        val root = rootInActiveWindow ?: return
        if (root.packageName != TARGET_PACKAGE) return

        if (!doneNotified && terminalSaysSetupComplete(root)) {
            doneNotified = true
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_SETUP_DONE, true)
                .putBoolean(KEY_PENDING_AUTOTYPE, false)
                .apply()
            Toast.makeText(
                applicationContext,
                "Setup complete — return to MobAntigravity",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING_AUTOTYPE, false)) return

        val now = System.currentTimeMillis()
        if (now - lastAttempt < 1000L) return
        lastAttempt = now

        val input = findInput(root) ?: return
        val command = (prefs.getString(KEY_COMMAND, SETUP_CMD) ?: SETUP_CMD) + "\n"
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                command
            )
        }
        if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            prefs.edit().putBoolean(KEY_PENDING_AUTOTYPE, false).apply()
            Toast.makeText(applicationContext, "Command sent to AntiGem", Toast.LENGTH_SHORT).show()
        }
    }

    private fun terminalSaysSetupComplete(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (text.contains("Setup complete", true) || desc.contains("Setup complete", true)) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { node ->
            if (isTextInput(node)) return node
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            if (node.isFocused && isTextInput(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun isTextInput(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString().orEmpty()
        return node.isEditable ||
            cls.contains("EditText", true) ||
            (node.actions and AccessibilityNodeInfo.ACTION_SET_TEXT) != 0
    }
}

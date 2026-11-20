package com.mobantigravity.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.ArrayDeque

class GemAutoTypeService : AccessibilityService() {

    companion object {
        const val PREFS_NAME = "mob_setup"
        const val KEY_PENDING_AUTOTYPE = "pending_autotype"
        const val KEY_COMMAND = "autotype_command"
        const val KEY_SETUP_DONE = "setup_done"
        private const val TARGET_PACKAGE = "ai.antigem"
        private const val SETUP_CMD = "bash /sdcard/Download/MobAntigravity/setup.sh"
    }

    private var lastAttempt = 0L
    private var doneNotified = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Configuration comes from @xml/accessibility_service_config.
        inspect()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        inspect()
    }

    override fun onInterrupt() {
        doneNotified = false
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
            Toast.makeText(applicationContext, "Setup command sent", Toast.LENGTH_SHORT).show()
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

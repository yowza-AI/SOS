package com.sos.app.trigger

/**
 * State machine for detecting rapid button presses.
 * Detects N presses within a time window.
 */
class TriggerManager(
    private val pressCount: Int = 4,
    private val timeWindowMs: Long = 2000,
    private val onTrigger: () -> Unit
) {
    private val pressTimestamps = mutableListOf<Long>()

    fun onButtonPress() {
        val now = System.currentTimeMillis()

        // Remove timestamps older than the time window
        pressTimestamps.removeAll { now - it > timeWindowMs }

        pressTimestamps.add(now)

        // Check if we've hit the press count within the window
        if (pressTimestamps.size >= pressCount) {
            pressTimestamps.clear()
            onTrigger()
        }
    }

    fun reset() {
        pressTimestamps.clear()
    }
}

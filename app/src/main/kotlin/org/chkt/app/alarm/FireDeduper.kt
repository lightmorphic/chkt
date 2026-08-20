package org.chkt.app.alarm

/**
 * Guards against duplicate alarm delivery. Neither AlarmReceiver nor the OS
 * guarantees exactly-once delivery: a redelivered broadcast, or a race with
 * the location-triggered path for the same reminder, are both possible, and
 * a duplicate would not just double the alert sound but double-advance a
 * repeat rule.
 *
 * Entries expire by timestamp comparison at the next call, never by a
 * delayed task — AlertService stops itself seconds after firing, and any
 * cleanup scheduled on its scope would die with it, leaving the key stuck
 * and swallowing every later nag re-alert of the same occurrence (which
 * shares the key, because nagging doesn't change dueAt).
 */
class FireDeduper(private val windowMillis: Long = 10_000L) {
    private val recentlyFired = HashMap<String, Long>()

    /** Records the firing and reports whether it duplicates one seen within
     * the window. Thread-safe. */
    fun isDuplicate(key: String, nowMillis: Long): Boolean = synchronized(recentlyFired) {
        recentlyFired.values.removeAll { nowMillis - it > windowMillis }
        recentlyFired.putIfAbsent(key, nowMillis) != null
    }
}

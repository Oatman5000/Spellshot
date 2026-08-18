package net.everlands.core

import java.util.concurrent.TimeUnit


fun Number.toPaddedTimeString(
    unit: TimeUnit = TimeUnit.MILLISECONDS,
    padding: Int = 2,
): String {
    val totalSeconds = unit.toSeconds(this.toLong())
    val units = MutableList(padding) { 0L }
    var remainder = totalSeconds
    for (i in padding - 1 downTo 1) {
        units[i] = remainder % 60
        remainder /= 60
    }
    units[0] = remainder
    return units.joinToString(":") { String.format("%02d", it) }
}

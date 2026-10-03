package com.karan.anuj.core.domain.time

/**
 * The single source of "now" for the whole app.
 *
 * Every timestamp is stored as epoch milliseconds in UTC and converted to the
 * device's zone only when it is shown. Reading the time through this interface
 * instead of the system clock lets tests pin the clock to a known instant.
 */
fun interface TimeSource {
    fun nowMillis(): Long
}

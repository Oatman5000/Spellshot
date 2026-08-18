package net.everlands.core.framework

open class Config<T : Any>(private val default: T) {
    @Volatile private var override: T? = null
    val value: T get() = override ?: default
    fun set(newValue: T) { override = newValue }
    fun reset() { override = null }
}

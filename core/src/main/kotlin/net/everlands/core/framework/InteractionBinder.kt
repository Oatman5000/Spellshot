package net.everlands.core.framework

import com.hypixel.hytale.codec.builder.BuilderCodec
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

abstract class InteractionBinder<T : Any>(
    val interactionClass: KClass<T>,
    val factory: () -> T,
    val codecFactory: () -> BuilderCodec.Builder<T>
) {
    init { @Suppress("LeakingThis") InteractionBinders.add(this) }
}

object InteractionBinders {
    private val all = CopyOnWriteArrayList<InteractionBinder<*>>()
    internal fun add(b: InteractionBinder<*>) { all.add(b) }
    fun all(): List<InteractionBinder<*>> = all
}

package net.spellshot.core.framework

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.concurrent.CopyOnWriteArrayList

abstract class ComponentDescriptor<C : Component<EntityStore>>(
    private val componentClass: Class<C>,
    val factory: () -> C,
    private val codecFactory: () -> BuilderCodec.Builder<C>
) {
    init { @Suppress("LeakingThis") ComponentDescriptors.add(this) }

    val name: String get() = componentClass.simpleName

    @Volatile private var type: ComponentType<EntityStore, C>? = null

    val componentType: ComponentType<EntityStore, C>
        get() {
            type?.let { return it }
            val plugin = SpellshotPlugin.INSTANCE ?: error("Component not registered yet: " + name)
            register(plugin)
            return type ?: error("Component not registered yet: " + name)
        }

    val isRegistered: Boolean get() = type != null

    internal fun register(plugin: JavaPlugin) {
        if (type != null) return
        type = plugin.entityStoreRegistry.registerComponent(
            componentClass, componentClass.simpleName, codecFactory().build()
        )
    }
}

object ComponentDescriptors {
    private val all = CopyOnWriteArrayList<ComponentDescriptor<*>>()
    internal fun add(d: ComponentDescriptor<*>) { all.add(d) }
    fun all(): List<ComponentDescriptor<*>> = all
}

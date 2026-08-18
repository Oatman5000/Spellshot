package net.everlands.moshpit.item.ammo.hytale

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.everlands.core.framework.ComponentDescriptor
import net.everlands.core.ext.builderCodec

class UnlimitedAmmoComponent : Component<EntityStore> {

    override fun clone() = UnlimitedAmmoComponent()

    companion object : ComponentDescriptor<UnlimitedAmmoComponent>(
        UnlimitedAmmoComponent::class.java,
        ::UnlimitedAmmoComponent,
        { builderCodec(::UnlimitedAmmoComponent) }
    )
}


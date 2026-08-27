package net.spellshot.moshpit.item.ammo.hytale

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.ComponentDescriptor
import net.spellshot.core.ext.builderCodec

class UnlimitedAmmoComponent : Component<EntityStore> {

    override fun clone() = UnlimitedAmmoComponent()

    companion object : ComponentDescriptor<UnlimitedAmmoComponent>(
        UnlimitedAmmoComponent::class.java,
        ::UnlimitedAmmoComponent,
        { builderCodec(::UnlimitedAmmoComponent) }
    )
}


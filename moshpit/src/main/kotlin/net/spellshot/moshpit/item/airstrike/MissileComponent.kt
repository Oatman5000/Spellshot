package net.spellshot.moshpit.item.airstrike

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.ComponentDescriptor
import net.spellshot.core.ext.builderCodec
import net.spellshot.core.ext.property

class MissileComponent(var id: String? = null) : Component<EntityStore> {

    val spawned = System.currentTimeMillis()

    override fun clone() = MissileComponent(
        id = id
    )

    companion object : ComponentDescriptor<MissileComponent>(
        MissileComponent::class.java,
        ::MissileComponent,
        {
            builderCodec(::MissileComponent)
                .property(MissileComponent::id, Codec.STRING)
        }
    )
}



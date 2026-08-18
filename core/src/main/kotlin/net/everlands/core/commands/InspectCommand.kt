package net.everlands.core.commands

import net.everlands.core.framework.Raycasts

import com.hypixel.hytale.server.core.util.TargetUtil
import net.everlands.core.framework.dsl.EverlandsCommand
import net.everlands.core.framework.dsl.buildPlayerCommand
import net.everlands.core.ext.uuidOrNull
import net.everlands.core.ext.sendRawMessage
import org.springframework.stereotype.Service

@Service
class InspectCommand : EverlandsCommand({
    buildPlayerCommand("inspect", "Inspect entities components") {
        // No try/catch here on purpose. The DSL now logs any handler throw and tells
        // the sender; this used to swallow it into a bare printStackTrace, which put
        // an unattributed trace on stdout and left the player staring at nothing.
        handle {
            val look = TargetUtil.getLook(ref, ref.store)
            val result = Raycasts.raycastEntity(world, look.position, look.direction, 5.0, { it != ref })
            val entity = result.hit
            if (entity == null) {
                context.sendRawMessage("No entity hit.")
                return@handle
            }
            val archetype = entity.store.getArchetype(entity)
            context.sendRawMessage("Hit entity UUID: ${entity.uuidOrNull?.uuid}")
            context.sendRawMessage("Components:")
            for (i in 0 until archetype.length()) {
                // Gaps in the archetype are reported to the player rather than stdout:
                // this is a debug command, so the holes are the interesting part.
                val type = archetype.get(i)
                if (type == null) {
                    context.sendRawMessage("- <no type at index $i>")
                    continue
                }
                val comp = entity.store.getComponent(entity, type)
                if (comp == null) {
                    context.sendRawMessage("- <no component for $type>")
                    continue
                }
                context.sendRawMessage("- ${comp::class.simpleName}")
            }
        }
    }
})


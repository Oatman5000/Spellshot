package net.everlands.core.framework

import org.springframework.stereotype.Service

/**
 * Replaces hyboot's HytaleService. Lets beans register work to run at a
 * lifecycle point from inside their constructor, which is how
 * InteractionRegistry and MiniGameEngine register codecs.
 */
@Service
class HytaleService {
    internal val setupCallbacks = mutableListOf<() -> Unit>()
    internal val startCallbacks = mutableListOf<() -> Unit>()

    fun onSetup(block: () -> Unit) { setupCallbacks += block }
    fun onStarted(block: () -> Unit) { startCallbacks += block }
}
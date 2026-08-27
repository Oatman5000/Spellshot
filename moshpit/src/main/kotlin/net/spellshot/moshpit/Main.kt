package net.spellshot.moshpit

import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import net.spellshot.core.framework.SpellshotPlugin
import org.springframework.context.annotation.ComponentScan

@ComponentScan(basePackages = ["net.spellshot"])
class Application

class MoshpitPlugin(init: JavaPluginInit) : SpellshotPlugin(init) {
    override fun configurationClass(): Class<*> = Application::class.java
}

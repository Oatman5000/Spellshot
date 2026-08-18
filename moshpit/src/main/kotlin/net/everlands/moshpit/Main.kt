package net.everlands.moshpit

import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import net.everlands.core.framework.EverlandsPlugin
import org.springframework.context.annotation.ComponentScan

@ComponentScan(basePackages = ["net.everlands"])
class Application

class MoshpitPlugin(init: JavaPluginInit) : EverlandsPlugin(init) {
    override fun configurationClass(): Class<*> = Application::class.java
}

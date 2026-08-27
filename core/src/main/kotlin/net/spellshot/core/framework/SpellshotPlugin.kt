package net.spellshot.core.framework

import com.hypixel.hytale.component.system.ISystem
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Service

abstract class SpellshotPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    private val logger = LoggerFactory.getLogger("spellshot")

    lateinit var context: AnnotationConfigApplicationContext
        private set

    protected abstract fun configurationClass(): Class<*>

    override fun setup() {
        INSTANCE = this
        // Before any bean can touch a user, so progression is never read as empty.
        UserStore.load()
        context = AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton("spellshotPlugin", this)
        context.register(configurationClass())
        val pluginLoader = javaClass.classLoader
        context.classLoader = pluginLoader
        context.beanFactory.beanClassLoader = pluginLoader
        val scanned = registerJarComponents(context, javaClass, pluginLoader)
        logger.info("Spellshot: registered {} component classes", scanned)
        val previousTccl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = pluginLoader
        try { context.refresh() } finally { Thread.currentThread().contextClassLoader = previousTccl }

        registerComponents()
        registerSystems()
        registerCommands()
        dispatch(OnSetup::class.java, "@OnSetup")
        context.getBeansOfType(HytaleService::class.java).values.forEach { svc ->
            svc.setupCallbacks.forEach { cb -> runCatching { cb() }.onFailure { logger.error("onSetup failed", it) } }
        }

        logger.info("Spellshot: {} beans initialised", context.beanDefinitionCount)
    }

    override fun start() {
        dispatch(OnStart::class.java, "@OnStart")
        context.getBeansOfType(HytaleService::class.java).values.forEach { svc ->
            svc.startCallbacks.forEach { cb -> runCatching { cb() }.onFailure { logger.error("onStarted failed", it) } }
        }
        UserStore.startAutoSave()
    }

    override fun shutdown() {
        // Save first: a hard kill still loses at most one auto-save interval,
        // but an orderly shutdown should never lose progress at all.
        UserStore.stopAutoSave()
        runCatching { UserStore.saveNow() }.onFailure { logger.error("Final progression save failed", it) }
        if (this::context.isInitialized) context.close()
        INSTANCE = null
    }

    private fun registerComponents() {
        for (descriptor in ComponentDescriptors.all()) {
            runCatching { descriptor.register(this) }
                .onFailure { logger.error("component registration failed: {}", descriptor.name, it) }
        }
    }

    private fun registerSystems() {
        for (bean in context.getBeansOfType(ISystem::class.java).values) {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                entityStoreRegistry.registerSystem(bean as ISystem<EntityStore>)
                logger.info("system: {}", bean.javaClass.simpleName)
            }.onFailure { logger.error("system registration failed: {}", bean.javaClass.name, it) }
        }
    }

    private fun registerCommands() {
        for (bean in context.getBeansOfType(AbstractCommand::class.java).values) {
            runCatching { commandRegistry.registerCommand(bean) }
                .onFailure { logger.error("command registration failed: {}", bean.javaClass.name, it) }
        }
        for (bean in context.getBeansOfType(net.spellshot.core.framework.dsl.SpellshotCommand::class.java).values) {
            for (cmd in bean.commands()) {
                runCatching { commandRegistry.registerCommand(cmd) }
                    .onFailure { logger.error("command registration failed: {}", cmd.javaClass.name, it) }
            }
        }
    }

    private fun dispatch(annotation: Class<out Annotation>, label: String) {
        for (bean in context.getBeansOfType(Any::class.java).values) {
            val target = AopUtils.getTargetClass(bean)
            for (method in target.declaredMethods) {
                if (!method.isAnnotationPresent(annotation)) continue
                runCatching {
                    method.isAccessible = true
                    method.invoke(bean)
                }.onFailure { logger.error("{} failed: {}.{}", label, target.simpleName, method.name, it) }
            }
        }
    }

    companion object {
        @Volatile @JvmStatic
        var INSTANCE: SpellshotPlugin? = null
            private set

        val plugin: JavaPlugin get() = INSTANCE ?: error("Spellshot plugin not initialised")
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class EcsService

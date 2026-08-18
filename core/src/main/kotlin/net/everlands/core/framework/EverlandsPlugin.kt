package net.everlands.core.framework

import com.hypixel.hytale.component.system.ISystem
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Service

abstract class EverlandsPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    private val log = LoggerFactory.getLogger("everlands")

    lateinit var context: AnnotationConfigApplicationContext
        private set

    protected abstract fun configurationClass(): Class<*>

    override fun setup() {
        INSTANCE = this
        // Before any bean can touch a user, so progression is never read as empty.
        UserStore.load()
        context = AnnotationConfigApplicationContext()
        context.beanFactory.registerSingleton("everlandsPlugin", this)
        context.register(configurationClass())
        val pluginLoader = javaClass.classLoader
        context.classLoader = pluginLoader
        context.beanFactory.beanClassLoader = pluginLoader
        val scanned = registerJarComponents(context, javaClass, pluginLoader)
        log.info("Everlands: registered {} component classes", scanned)
        val previousTccl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = pluginLoader
        try { context.refresh() } finally { Thread.currentThread().contextClassLoader = previousTccl }

        registerComponents()
        registerSystems()
        registerCommands()
        dispatch(OnSetup::class.java, "@OnSetup")
        context.getBeansOfType(HytaleService::class.java).values.forEach { svc ->
            svc.setupCallbacks.forEach { cb -> runCatching { cb() }.onFailure { log.error("onSetup failed", it) } }
        }

        log.info("Everlands: {} beans initialised", context.beanDefinitionCount)
    }

    override fun start() {
        dispatch(OnStart::class.java, "@OnStart")
        context.getBeansOfType(HytaleService::class.java).values.forEach { svc ->
            svc.startCallbacks.forEach { cb -> runCatching { cb() }.onFailure { log.error("onStarted failed", it) } }
        }
        UserStore.startAutoSave()
    }

    override fun shutdown() {
        // Save first: a hard kill still loses at most one auto-save interval,
        // but an orderly shutdown should never lose progress at all.
        UserStore.stopAutoSave()
        runCatching { UserStore.saveNow() }.onFailure { log.error("Final progression save failed", it) }
        if (this::context.isInitialized) context.close()
        INSTANCE = null
    }

    private fun registerComponents() {
        for (descriptor in ComponentDescriptors.all()) {
            runCatching { descriptor.register(this) }
                .onFailure { log.error("component registration failed: {}", descriptor.name, it) }
        }
    }

    private fun registerSystems() {
        for (bean in context.getBeansOfType(ISystem::class.java).values) {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                entityStoreRegistry.registerSystem(bean as ISystem<EntityStore>)
                log.info("system: {}", bean.javaClass.simpleName)
            }.onFailure { log.error("system registration failed: {}", bean.javaClass.name, it) }
        }
    }

    private fun registerCommands() {
        for (bean in context.getBeansOfType(AbstractCommand::class.java).values) {
            runCatching { commandRegistry.registerCommand(bean) }
                .onFailure { log.error("command registration failed: {}", bean.javaClass.name, it) }
        }
        for (bean in context.getBeansOfType(net.everlands.core.framework.dsl.EverlandsCommand::class.java).values) {
            for (cmd in bean.commands()) {
                runCatching { commandRegistry.registerCommand(cmd) }
                    .onFailure { log.error("command registration failed: {}", cmd.javaClass.name, it) }
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
                }.onFailure { log.error("{} failed: {}.{}", label, target.simpleName, method.name, it) }
            }
        }
    }

    companion object {
        @Volatile @JvmStatic
        var INSTANCE: EverlandsPlugin? = null
            private set

        val plugin: JavaPlugin get() = INSTANCE ?: error("Everlands plugin not initialised")
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class EcsService

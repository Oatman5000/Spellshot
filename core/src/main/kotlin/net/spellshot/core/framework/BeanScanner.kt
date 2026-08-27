package net.spellshot.core.framework

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Spring resolves @ComponentScan candidates by walking the classpath. Inside a Hytale
 * plugin classloader that turns up nothing, so scanning silently finds no beans.
 * Walk our own jar instead and register every @Component-meta-annotated class directly.
 */
internal fun registerJarComponents(
    context: AnnotationConfigApplicationContext,
    owner: Class<*>,
    loader: ClassLoader,
): Int {
    val location = owner.protectionDomain?.codeSource?.location ?: return 0
    val file = runCatching { File(location.toURI()) }.getOrNull() ?: return 0
    if (!file.isFile) return 0

    val candidates = ArrayList<Class<*>>()
    JarFile(file).use { jar ->
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            // MUST track the package root. This is a string, so the compiler cannot
            // catch it drifting: point it at a package that does not exist and the
            // scan silently finds nothing, every @Service disappears, and the plugin
            // boots with zero beans and no error.
            if (!name.startsWith("net/spellshot/") || !name.endsWith(".class")) continue
            val className = name.substring(0, name.length - 6).replace('/', '.')
            val cls = runCatching { Class.forName(className, false, loader) }.getOrNull() ?: continue
            if (cls.isInterface || cls.isEnum || cls.isAnnotation) continue
            if (cls.isAnonymousClass || cls.isLocalClass || cls.isSynthetic) continue
            if (Modifier.isAbstract(cls.modifiers)) continue
            if (cls.isMemberClass && !Modifier.isStatic(cls.modifiers)) continue
            val kotlinObject = runCatching {
                val f = cls.getDeclaredField("INSTANCE")
                Modifier.isStatic(f.modifiers) && f.type == cls
            }.getOrDefault(false)
            if (kotlinObject) continue
            val component = runCatching {
                AnnotatedElementUtils.hasAnnotation(cls, Component::class.java)
            }.getOrDefault(false)
            if (component) candidates.add(cls)
        }
    }

    var registered = 0
    for (cls in candidates) {
        runCatching { context.register(cls); registered++ }
            // A bean that fails to register does not throw anywhere else -- it simply
            // never exists, and the feature it backs quietly does nothing. That is a
            // very expensive thing to debug from the symptom, so name it here.
            .onFailure { scannerLog.error("Failed to register bean {}", cls.name, it) }
    }
    return registered
}

private val scannerLog = org.slf4j.LoggerFactory.getLogger("spellshot.beans")
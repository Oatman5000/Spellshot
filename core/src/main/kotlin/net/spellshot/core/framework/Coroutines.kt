package net.spellshot.core.framework

import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.universe.world.World
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.launch as coLaunch

private val dispatcher = HytaleServer.SCHEDULED_EXECUTOR.asCoroutineDispatcher()

object SpellshotScope {
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
}

/**
 * Resumes coroutines on the owning world's task queue. The ECS Store
 * hard-asserts it is only touched from its own world thread, so anything
 * that reads or writes components must run here.
 */
class WorldDispatcher(private val world: World) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        try {
            world.execute { block.run() }
        } catch (e: IllegalThreadStateException) {
            // Expected on shutdown: World.execute throws once the world thread
            // stops accepting tasks, and any still-pending delayed work (the HUD
            // re-apply timers, vote countdowns) lands here. Dropping the block is
            // correct -- the world is going away. Rethrowing surfaces it as an
            // uncaught DispatchException on the coroutine executor thread.
            LoggerFactory.getLogger("spellshot").debug(
                "Dropped world task after shutdown: {}", e.message
            )
        }
    }
}

val World.coroutineDispatcher: CoroutineDispatcher
    get() = WorldDispatcher(this)

fun World.launch(block: suspend CoroutineScope.() -> Unit): Job =
    SpellshotScope.scope.coLaunch(context = this.coroutineDispatcher, block = block)

fun launchGlobal(block: suspend CoroutineScope.() -> Unit): Job =
    SpellshotScope.scope.coLaunch(block = block)

/** Fire-and-forget: schedules [block] on the world thread and returns immediately. */
fun World.context(block: suspend () -> Unit): Job =
    SpellshotScope.scope.coLaunch(context = this.coroutineDispatcher) { block() }

/**
 * Runs [block] on the world thread and *waits* for it, returning its value.
 *
 * Use this instead of [context] whenever later code depends on the block having
 * finished -- e.g. populating a list that the next statement iterates.
 */
suspend fun <T> World.onWorld(block: suspend () -> T): T =
    withContext(this.coroutineDispatcher) { block() }
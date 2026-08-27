package net.spellshot.core.framework.dsl

import com.hypixel.hytale.component.Ref
import kotlinx.coroutines.launch
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

class HandleScope(
    val context: CommandContext,
    val store: Store<EntityStore>,
    val ref: Ref<EntityStore>,
    val playerRef: PlayerRef,
    val world: World
) {
    val player: Player? get() = store.getComponent(ref, Player.getComponentType())
}

open class CommandSpec(val name: String, val description: String = "") {

    open val command: AbstractCommand by lazy { SpecCommand(this) }

    internal var handler: (suspend HandleScope.() -> Unit)? = null
    internal var live: CommandContext? = null

    fun handle(block: suspend HandleScope.() -> Unit) { handler = block }

    fun <D> required(argName: String, argDescription: String, argType: ArgumentType<D>): () -> D {
        val arg = command.withRequiredArg(argName, argDescription, argType)
        return { live?.get(arg) ?: error("Argument read outside handle: " + argName) }
    }

    inline fun <reified T> required(argName: String, argDescription: String = ""): () -> T {
        @Suppress("UNCHECKED_CAST")
        val argType = when (T::class) {
            String::class -> ArgTypes.STRING
            Int::class -> ArgTypes.INTEGER
            else -> error("No ArgTypes mapping")
        } as ArgumentType<T>
        return required(argName, argDescription, argType)
    }
}

class CommandCollectionSpec(name: String, description: String = "") : CommandSpec(name, description) {
    internal val children = mutableListOf<CommandSpec>()

    /**
     * A container node is an [AbstractCommandCollection], not an [AbstractPlayerCommand].
     *
     * This is the class vanilla uses for every non-leaf node -- `/hitboxcollision`,
     * `/hitboxcollision add`, `/knockback apply` and so on. The difference that
     * matters is `isNoArgVariant()`, which tells the dispatcher the node carries no
     * arguments of its own and that trailing tokens belong to a subcommand.
     *
     * Building containers as AbstractPlayerCommand instead meant `/arena` declared
     * zero required arguments and then rejected the four tokens of
     * `loc set nuketown spawn:1` outright -- "The wrong number of required argument
     * was specified. Expected: 0, actual: 5" -- without ever looking at its own
     * subcommand table, which the printed usage proved was correctly populated.
     *
     * A container has no handler; `handle { }` on a collection does nothing.
     */
    override val command: AbstractCommand by lazy { SpecCommandCollection(this) }

    fun command(name: String, description: String = "", block: CommandSpec.() -> Unit) {
        children += CommandSpec(name, description).apply(block)
    }

    fun collection(name: String, description: String = "", block: CommandCollectionSpec.() -> Unit) {
        children += CommandCollectionSpec(name, description).apply(block)
    }

    fun playerCommand(name: String, description: String = "", block: CommandSpec.() -> Unit) {
        children += CommandSpec(name, description).apply(block)
    }
}

/** Container node: dispatches to subcommands and prints usage. Never executes itself. */
class SpecCommandCollection(spec: CommandCollectionSpec) :
    AbstractCommandCollection(spec.name, spec.description) {

    // 0.6.0 removed the `canGeneratePermission()` hook this used to override and
    // replaced it with a call: requireNoPermission(). Same effect -- suppress the
    // auto-generated permission node -- but it is now imperative rather than a
    // template method, so it has to run during construction.
    //
    // Anything that wants a real permission still calls requirePermission() after
    // the fact, as ArenaCommand does with requireGamePermission("arena"); that runs
    // later and wins. It must happen before registration completes, though --
    // AbstractCommand throws "Cannot change permissions when a command has already
    // completed registration" otherwise.
    init {
        requireNoPermission()
    }
}

class SpecCommand(private val spec: CommandSpec) : AbstractPlayerCommand(spec.name, spec.description) {

    /** See the note on [SpecCommandCollection]; 0.6.0 turned this into a call. */
    init {
        requireNoPermission()
    }

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World
    ) {
        // Logged before the handler is queued so the console distinguishes three
        // otherwise identical-looking failures: no line at all means the command
        // never resolved; a line with no follow-up means the handler was queued but
        // never ran; a line followed by the catch below means the handler threw.
        logger.info("Executing command '{}'", spec.name)
        spec.live = context
        val scope = HandleScope(context, store, ref, playerRef, world)
        // Hytale calls execute() on the owning world thread, and the ECS store
        // asserts that. Dispatch coroutine resumptions back onto that world's
        // queue so suspending handlers stay on the right thread.
        val worldDispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                world.execute { block.run() }
            }
        }
        kotlinx.coroutines.CoroutineScope(worldDispatcher).launch {
            try {
                spec.handler?.invoke(scope)
            } catch (t: Throwable) {
                // Without this, handlers fail COMPLETELY silently: `launch` has no
                // exception handler and the dispatcher is a bare world.execute, so a
                // throw never reaches an uncaught-exception handler. Nothing is
                // logged, nothing is sent, the command just appears to do nothing.
                // That is how `/arena loc set` looked when it was actually throwing.
                logger.error("Command '${spec.name}' failed", t)
                // Unlogged on purpose: the error is already in the console above, and
                // if the sender has gone away there is nothing useful to add.
                runCatching {
                    context.sendMessage(
                        com.hypixel.hytale.server.core.Message.raw(
                            "Command failed: ${t::class.simpleName}: ${t.message}"
                        )
                    )
                }
            } finally {
                spec.live = null
            }
        }
    }

    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SpecCommand::class.java)
    }
}

fun buildCommand(name: String, description: String = "", block: CommandSpec.() -> Unit): CommandSpec =
    CommandSpec(name, description).apply(block)

fun buildPlayerCommand(name: String, description: String = "", block: CommandSpec.() -> Unit): CommandSpec =
    CommandSpec(name, description).apply(block)

fun buildCommandCollection(
    name: String, description: String = "", block: CommandCollectionSpec.() -> Unit
): CommandCollectionSpec = CommandCollectionSpec(name, description).apply(block)

abstract class SpellshotCommand(definition: () -> CommandSpec) {

    private val spec: CommandSpec by lazy { definition() }

    fun commands(): List<AbstractCommand> {
        val root = spec
        attachChildren(root)
        return listOf(root.command)
    }

    /**
     * Registers subcommands at every depth, not just the first.
     *
     * This used to be a flat loop over the root's children, which meant a
     * `collection { }` nested inside another collection had its own children
     * silently dropped: the container command was registered, but with nothing
     * under it. `/arena loc set nuketown spawn:1` therefore resolved to a bare
     * `arena loc` taking zero arguments and failed with "The wrong number of
     * required argument was specified. Expected: 0, actual: 3" -- an error that
     * points at argument counts and gives no hint that the subcommand itself was
     * never wired up.
     *
     * Hytale supports arbitrary nesting; vanilla ships three-level commands such
     * as `/hitboxcollision add entity` and `/knockback apply aimed`. The DSL just
     * was not recursing.
     */
    private fun attachChildren(spec: CommandSpec) {
        if (spec !is CommandCollectionSpec) return
        for (child in spec.children) {
            // Depth first, and attach to the parent LAST. AbstractCommand throws
            // "Cannot add new subcommands when a command has already completed
            // registration" -- handing a node to its parent can finalise it, so a
            // node has to be fully populated before it is handed over. Attaching
            // `loc` to `arena` and only then adding `set`/`del` to `loc` is the
            // wrong way round.
            attachChildren(child)
            runCatching { spec.command.addSubCommand(child.command) }.onFailure {
                // A wiring mistake in one command must not take the server down
                // with it. Log loudly and carry on: the rest of the tree, and the
                // rest of the plugin, still work.
                logger.error(
                    "Failed to register subcommand '${child.name}' under '${spec.name}': ${it.message}", it
                )
            }
        }
    }

    private companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SpellshotCommand::class.java)
    }
}

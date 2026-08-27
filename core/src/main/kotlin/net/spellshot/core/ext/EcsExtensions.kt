package net.spellshot.core.ext

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.builder.BuilderField
import com.hypixel.hytale.codec.codecs.map.ObjectMapCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.framework.MessageParser
import org.joml.Vector3d
import org.slf4j.LoggerFactory
import kotlin.reflect.KMutableProperty1

private val ecsLog = LoggerFactory.getLogger("EcsExtensions")

val Ref<EntityStore>.transformOrNull: TransformComponent?
    get() = store.getComponent(this, TransformComponent.getComponentType())

val Ref<EntityStore>.transform: TransformComponent
    get() = transformOrNull ?: error("Entity has no TransformComponent")

fun Store<EntityStore>.transform(ref: Ref<EntityStore>): TransformComponent =
    getComponent(ref, TransformComponent.getComponentType()) ?: error("Entity has no TransformComponent")

fun ArchetypeChunk<EntityStore>.transformOrNull(index: Int): TransformComponent? =
    getComponent(index, TransformComponent.getComponentType())

val Ref<EntityStore>.velocityOrNull: Velocity?
    get() = store.getComponent(this, Velocity.getComponentType())

fun ArchetypeChunk<EntityStore>.velocityOrNull(index: Int): Velocity? =
    getComponent(index, Velocity.getComponentType())

val Ref<EntityStore>.uuidOrNull: PlayerRef?
    get() = store.getComponent(this, PlayerRef.getComponentType())

val Ref<EntityStore>.networkIdOrNull: Int?
    get() = store.getComponent(this, Player.getComponentType())?.networkId

val Ref<EntityStore>.world get() = store.externalData.world

fun PlayerRef.estimateWorldTickLatency(): Long = 50L

fun <T : Any> CommandBuffer<EntityStore>.putModel(ref: Ref<EntityStore>, model: T) {
    ecsLog.debug("putModel - no-op")
}

fun <T : Any> CommandBuffer<EntityStore>.putNameplate(ref: Ref<EntityStore>, nameplate: T) {
    ecsLog.debug("putNameplate - no-op")
}

fun CommandBuffer<EntityStore>.removeNameplate(ref: Ref<EntityStore>) {
    ecsLog.debug("removeNameplate - no-op")
}

fun Ref<EntityStore>.addTeleport(transform: Transform) {
    this.store.addComponent(
        this,
        com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType(),
        com.hypixel.hytale.server.core.modules.entity.teleport.Teleport(
            this.store.externalData.world, Vector3d(transform.position), transform.rotation.clone()
        )
    )
}

/*
 * Codec DSL, replacing kotale's. Verified against the Server jar:
 *   BuilderCodec.builder(Class, Supplier) -> Builder
 *   Builder.append(KeyedCodec, setter, getter) -> FieldBuilder
 *   FieldBuilder.add() -> Builder
 *   Builder.build() -> BuilderCodec
 * The key comes from the Kotlin property name, as kotale did.
 */

fun <T : Any> builderCodec(factory: () -> T): BuilderCodec.Builder<T> {
    @Suppress("UNCHECKED_CAST")
    val cls = factory().javaClass as Class<T>
    return BuilderCodec.builder(cls, factory)
}

fun <T : Any, V> BuilderCodec.Builder<T>.property(
    prop: KMutableProperty1<T, V>,
    codec: Codec<V>
): BuilderCodec.Builder<T> =
    this.append(
        KeyedCodec(prop.name.replaceFirstChar { it.uppercaseChar() }, codec),
        java.util.function.BiConsumer<T, V> { o, v -> prop.set(o, v) },
        java.util.function.Function<T, V> { o -> prop.get(o) }
    ).add()

fun <T : Any, V> BuilderCodec.Builder<T>.property(
    prop: KMutableProperty1<T, V>,
    codec: Codec<V>,
    configure: BuilderField.FieldBuilder<T, V, BuilderCodec.Builder<T>>.() -> Unit
): BuilderCodec.Builder<T> =
    this.append(
        KeyedCodec(prop.name.replaceFirstChar { it.uppercaseChar() }, codec),
        java.util.function.BiConsumer<T, V> { o, v -> prop.set(o, v) },
        java.util.function.Function<T, V> { o -> prop.get(o) }
    ).apply(configure).add()

/** Replaces kotale's Codec<V>.map() - a String-keyed map of V. */
fun <V> Codec<V>.map(): Codec<MutableMap<String, V>> {
    @Suppress("UNCHECKED_CAST")
    return ObjectMapCodec<String, V, MutableMap<String, V>>(
        this, { LinkedHashMap() }, { it }, { it }
    ) as Codec<MutableMap<String, V>>
}

fun PlayerRef.sendRawMessage(message: String) = this.sendMessage(MessageParser.parse(message))
fun PlayerRef.sendMessageFormat(message: String) = this.sendMessage(MessageParser.parse(message))

fun com.hypixel.hytale.server.core.universe.world.World.sendMessage(
    message: com.hypixel.hytale.server.core.Message
) {
    com.hypixel.hytale.server.core.universe.Universe.get().players.forEach { it.sendMessage(message) }
}

fun com.hypixel.hytale.server.core.universe.world.World.sendMessage(message: String) =
    sendMessage(MessageParser.parse(message))

fun com.hypixel.hytale.server.core.universe.world.World.sendRawMessage(message: String) =
    sendMessage(MessageParser.parse(message))

/* Receiver overloads so playerRefOrNull / world work on Ref and PlayerRef too. */

val Ref<EntityStore>.playerRefOrNull: PlayerRef?
    get() = store.getComponent(this, PlayerRef.getComponentType())

val PlayerRef.playerRefOrNull: PlayerRef?
    get() = this

val PlayerRef.world: com.hypixel.hytale.server.core.universe.world.World?
    get() = this.reference?.store?.externalData?.world

val net.spellshot.core.framework.SpellshotUser.playerRefOrNull: PlayerRef?
    get() = this.playerRef
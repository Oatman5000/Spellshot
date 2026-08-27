package net.spellshot.core.framework

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.math.iterator.BlockIterator
import com.hypixel.hytale.math.shape.Box
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.selector.Selector
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.transformOrNull
import org.joml.Vector3d
import org.slf4j.LoggerFactory

data class BlockRaycastResult(val lastPos: org.joml.Vector3d, val hitPos: org.joml.Vector3d?)

/**
 * Voxel raycast against world geometry.
 *
 * Walks the blocks along the ray with the engine's [BlockIterator] and stops at
 * the first block whose actual hitbox the ray enters. [BlockRaycastResult.lastPos]
 * is where the ray ends -- the impact point on a hit, otherwise the far end of
 * its range -- so callers can use it directly as the end of a follow-up entity
 * sweep.
 *
 * Shapes matter here. This used to stop at any block with a non-empty id, which
 * treated every block as a full cube, so slabs, thin roof pieces and decorative
 * blocks swallowed shots that visually passed clean over them. The real shape
 * comes from the block's [BlockBoundingBoxes] asset, rotated to match how the
 * block was actually placed. Blocks with no boxes at all (foliage and similar)
 * now correctly stop nothing.
 *
 * The procedure contract is "return true to keep going": returning false stops
 * the traversal.
 */
object BlockBoxRaycaster {

    private val logger = LoggerFactory.getLogger("BlockBoxRaycaster")

    /** Fallback when a block's hitbox asset cannot be resolved: assume a full cube. */
    private val FULL_CUBE = arrayOf(Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))

    fun raycast(
        world: World,
        origin: Vector3d,
        direction: Vector3d,
        maxDistance: Double
    ): BlockRaycastResult {
        val dir = Vector3d(direction)
        if (dir.lengthSquared() <= 0.0 || maxDistance <= 0.0) {
            return BlockRaycastResult(Vector3d(origin), null)
        }
        dir.normalize()
        val end = Vector3d(origin).add(Vector3d(dir).mul(maxDistance))

        var hit: Vector3d? = null
        val probe = object : BlockIterator.BlockIteratorProcedure {
            override fun accept(
                x: Int, y: Int, z: Int,
                a: Double, b: Double, c: Double, d: Double, e: Double, f: Double
            ): Boolean {
                val t = hitDistance(world, origin, dir, x, y, z) ?: return true
                hit = Vector3d(origin).add(Vector3d(dir).mul(t))
                return false
            }
        }

        try {
            BlockIterator.iterate(origin, dir, maxDistance, probe)
        } catch (ex: Exception) {
            logger.warn("block raycast failed: ${ex.message}")
            return BlockRaycastResult(end, null)
        }

        val stop = hit
        return BlockRaycastResult(stop ?: end, stop)
    }

    /**
     * Distance along the ray at which it enters this block's geometry, or null
     * if it misses -- which includes passing over a slab or through a block that
     * has no collision boxes.
     */
    private fun hitDistance(
        world: World, origin: Vector3d, dir: Vector3d, bx: Int, by: Int, bz: Int
    ): Double? {
        val id = try {
            world.getBlock(bx, by, bz)
        } catch (ex: Exception) {
            // Unloaded/out-of-range chunk: treat as empty and keep going.
            return null
        }
        if (id == BlockType.EMPTY_ID) return null

        var best: Double? = null
        for (box in collisionBoxes(world, bx, by, bz)) {
            // Boxes are block-local, so shift them onto the block's world position.
            val t = rayBox(
                origin, dir,
                bx + box.min.x, by + box.min.y, bz + box.min.z,
                bx + box.max.x, by + box.max.y, bz + box.max.z
            ) ?: continue
            if (best == null || t < best) best = t
        }
        return best
    }

    /**
     * The block's collision boxes in block-local space.
     *
     * Detail boxes are preferred where present (stairs, fences and the like are
     * several boxes); otherwise the single bounding box is used. The rotation
     * index matters -- without it an upside-down slab would be treated as a
     * bottom slab and shots would pass through the solid half.
     */
    private fun collisionBoxes(world: World, bx: Int, by: Int, bz: Int): Array<Box> {
        return try {
            val type = world.getBlockType(bx, by, bz) ?: return FULL_CUBE
            val asset = BlockBoundingBoxes.getAssetMap()
                .getAsset(type.hitboxTypeIndex) as? BlockBoundingBoxes ?: return FULL_CUBE
            // 0.6.0 dropped IChunkAccessorSync.getBlockRotationIndex(x, y, z). The
            // chunk still exposes it; only the World-level convenience wrapper went,
            // so do the lookup the wrapper used to do. getChunkIfLoaded rather than
            // getChunk because this runs on the shooting path -- a ray that leaves
            // loaded terrain should fall back to a cube, not block on chunk loading.
            val chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(bx, bz))
                ?: return FULL_CUBE
            val variant = asset.get(chunk.getRotationIndex(bx, by, bz)) ?: return FULL_CUBE
            if (variant.hasDetailBoxes()) variant.detailBoxes else arrayOf(variant.boundingBox)
        } catch (ex: Exception) {
            // Never let a shape lookup break shooting; fall back to a solid cube.
            FULL_CUBE
        }
    }

    /**
     * Slab-method intersection of the ray against an arbitrary axis-aligned box,
     * returning the entry distance rather than the block centre.
     */
    private fun rayBox(
        origin: Vector3d, dir: Vector3d,
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double
    ): Double? {
        var tNear = Double.NEGATIVE_INFINITY
        var tFar = Double.POSITIVE_INFINITY
        val o = doubleArrayOf(origin.x, origin.y, origin.z)
        val d = doubleArrayOf(dir.x, dir.y, dir.z)
        val lo = doubleArrayOf(minX, minY, minZ)
        val hi = doubleArrayOf(maxX, maxY, maxZ)

        for (i in 0..2) {
            if (lo[i] >= hi[i]) return null // degenerate axis: no volume to hit
            if (kotlin.math.abs(d[i]) < 1e-9) {
                if (o[i] < lo[i] || o[i] > hi[i]) return null
                continue
            }
            var t1 = (lo[i] - o[i]) / d[i]
            var t2 = (hi[i] - o[i]) / d[i]
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tNear) tNear = t1
            if (t2 < tFar) tFar = t2
            if (tNear > tFar) return null
        }
        if (tFar < 0.0) return null // box is entirely behind the ray
        val t = if (tNear < 0.0) 0.0 else tNear
        if (!t.isFinite()) return null
        return t
    }
}

object Raycasts {

    private val logger = LoggerFactory.getLogger("Raycasts")

    /**
     * Entity hit volume: an upright box standing ON the entity position.
     *
     * transform.position sits at the entity's FEET -- which is what
     * ShootInteraction's damage zones already assume, since its headDistance of
     * 1.38 and chestDistance of 0.8 are heights measured up from that point.
     *
     * This used to be a 0.6-radius sphere centred on that same point, so the
     * only hittable volume was a ball around the player's ankles and every shot
     * above the shins missed. The box spans feet to head instead.
     */
    @JvmStatic var HIT_HALF_WIDTH: Double = 0.4
    @JvmStatic var HIT_HEIGHT: Double = 1.85

    data class RaycastResult(
        val hit: Ref<EntityStore>? = null,
        val position: Vector3d? = null,
        val distance: Double = 0.0
    )

    data class LineCheckResult(
        val distance: Double = 0.0,
        val position: Vector3d? = null,
        val entity: Ref<EntityStore>? = null
    )

    fun raycastEntity(
        world: World,
        origin: Vector3d,
        direction: Vector3d,
        range: Double,
        filter: (Ref<EntityStore>) -> Boolean = { true }
    ): RaycastResult {
        val dir = Vector3d(direction).normalize()
        var best: Ref<EntityStore>? = null
        var bestT = Double.MAX_VALUE
        var bestPoint: Vector3d? = null

        val mid = Vector3d(origin).add(Vector3d(dir).mul(range / 2.0))
        try {
            Selector.selectNearbyEntities(world.entityStore.store, mid, range, { ref ->
                if (!filter(ref)) return@selectNearbyEntities
                val t = ref.transformOrNull ?: return@selectNearbyEntities
                val hit = intersect(origin, dir, t.position, range) ?: return@selectNearbyEntities
                if (hit < bestT) {
                    bestT = hit
                    best = ref
                    bestPoint = Vector3d(origin).add(Vector3d(dir).mul(hit))
                }
            }, { filter(it) })
        } catch (ex: Exception) {
            logger.warn("raycastEntity failed: " + ex.message)
        }
        return RaycastResult(best, bestPoint, if (best != null) bestT else 0.0)
    }

    fun lineCheckEntities(
        world: World,
        start: Vector3d,
        end: Vector3d,
        entityPredicate: (Ref<EntityStore>) -> Boolean = { true },
        tickRewind: Long = 0
    ): LineCheckResult {
        val delta = Vector3d(end).sub(start)
        val range = delta.length()
        if (range <= 0.0) return LineCheckResult()
        val r = raycastEntity(world, start, delta.normalize(), range, entityPredicate)
        return LineCheckResult(r.distance, r.position, r.hit)
    }

    fun debugBoundingBox(
        playerRef: PlayerRef,
        box: Any?,
        position: Vector3d,
        offset: Vector3d,
        color: Any?,
        durationMs: Int = 0
    ) {
        logger.debug("debugBoundingBox - no-op in this build")
    }

    /**
     * Slab-method ray test against the entity's hit box, returning the distance
     * along the ray at which it enters.
     *
     * [feet] is transform.position. The box is centred horizontally on it and
     * runs upward from it, so the entity is hittable over its whole height
     * rather than only near the ground.
     */
    private fun intersect(
        origin: Vector3d, dir: Vector3d, feet: Vector3d, maxRange: Double
    ): Double? {
        val hw = HIT_HALF_WIDTH
        val lo = doubleArrayOf(feet.x - hw, feet.y, feet.z - hw)
        val hi = doubleArrayOf(feet.x + hw, feet.y + HIT_HEIGHT, feet.z + hw)
        val o = doubleArrayOf(origin.x, origin.y, origin.z)
        val d = doubleArrayOf(dir.x, dir.y, dir.z)

        var tNear = Double.NEGATIVE_INFINITY
        var tFar = Double.POSITIVE_INFINITY
        for (i in 0..2) {
            if (kotlin.math.abs(d[i]) < 1e-9) {
                // Ray is parallel to this pair of planes: it can only hit if it
                // already lies between them.
                if (o[i] < lo[i] || o[i] > hi[i]) return null
                continue
            }
            var t1 = (lo[i] - o[i]) / d[i]
            var t2 = (hi[i] - o[i]) / d[i]
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tNear) tNear = t1
            if (t2 < tFar) tFar = t2
            if (tNear > tFar) return null
        }
        if (tFar < 0.0) return null // box is behind the shooter
        val t = if (tNear < 0.0) 0.0 else tNear
        if (!t.isFinite() || t > maxRange) return null
        return t
    }
}

package net.spellshot.core.functions

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.*
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.spellshot.core.ext.networkIdOrNull
import net.spellshot.core.ext.referenceOrNull

fun cameraOffset(playerRef: PlayerRef, offset: Float = 2.5f) {
    val cameraSettings = ServerCameraSettings()
    cameraSettings.isFirstPerson = false
    cameraSettings.attachedToType = AttachedToType.LocalPlayer;
    cameraSettings.eyeOffset = true
    cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
    cameraSettings.distance = offset
    cameraSettings.positionType = PositionType.AttachedToPlusOffset
    cameraSettings.rotationType = RotationType.AttachedToPlusOffset
    cameraSettings.applyLookType = ApplyLookType.LocalPlayerLookOrientation
    cameraSettings.rotationOffset = null
    cameraSettings.positionLerpSpeed = 1.0F
    cameraSettings.rotationLerpSpeed = 1.0F
    cameraSettings.skipCharacterPhysics = false
    cameraSettings.mouseInputType = MouseInputType.LookAtTarget
    cameraSettings.mouseInputTargetType = MouseInputTargetType.Any
    cameraSettings.sendMouseMotion = true
    cameraSettings.allowPitchControls = true
    cameraSettings.displayReticle = false
    cameraSettings.displayCursor = false
    playerRef.packetHandler.writeNoCache(SetServerCamera(ClientCameraView.Custom, false, cameraSettings))
}

fun cameraLookAtFree(playerRef: PlayerRef, targetRef: Ref<EntityStore>, distance: Float = 2.5f, rotationOffset: Direction = Direction(Math.toRadians(180.0).toFloat(), Math.toRadians(-35.0).toFloat() , 0f)) {
    val targetId = targetRef.networkIdOrNull ?: return
    val cameraSettings = ServerCameraSettings()
    cameraSettings.isFirstPerson = true
    cameraSettings.attachedToType = AttachedToType.EntityId;
    cameraSettings.attachedToEntityId = targetId
    cameraSettings.eyeOffset = true
    cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
    cameraSettings.distance = distance
    cameraSettings.positionType = PositionType.AttachedToPlusOffset
    cameraSettings.rotationType = RotationType.Custom
    cameraSettings.applyLookType = ApplyLookType.Rotation
    cameraSettings.rotation = rotationOffset
    cameraSettings.positionLerpSpeed = 1F
    cameraSettings.rotationLerpSpeed = 1F
    cameraSettings.skipCharacterPhysics = false
    cameraSettings.mouseInputType = MouseInputType.LookAtTargetEntity
    cameraSettings.mouseInputTargetType = MouseInputTargetType.Any
    cameraSettings.sendMouseMotion = true
    cameraSettings.allowPitchControls = true
    cameraSettings.displayReticle = false
    cameraSettings.displayCursor = false
    playerRef.packetHandler.writeNoCache(SetServerCamera(ClientCameraView.Custom, false, cameraSettings))
}

fun cameraLookAt(playerRef: PlayerRef, targetRef: PlayerRef, distance: Float = 2.5f) {
    val targetReference = targetRef.referenceOrNull ?: return
    val targetId = targetReference.networkIdOrNull ?: return
    val cameraSettings = ServerCameraSettings()
    cameraSettings.isFirstPerson = false
    cameraSettings.attachedToType = AttachedToType.EntityId;
    cameraSettings.attachedToEntityId = targetId
    cameraSettings.eyeOffset = true
    cameraSettings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
    cameraSettings.distance = distance
    cameraSettings.positionType = PositionType.AttachedToPlusOffset
    cameraSettings.rotationType = RotationType.AttachedToPlusOffset
    cameraSettings.applyLookType = ApplyLookType.Rotation
    cameraSettings.rotationOffset = Direction(Math.PI.toFloat(), 0f, 0f)
    cameraSettings.positionLerpSpeed = 1.0F
    cameraSettings.rotationLerpSpeed = 1.0F
    cameraSettings.skipCharacterPhysics = false
    cameraSettings.mouseInputType = MouseInputType.LookAtTarget
    cameraSettings.mouseInputTargetType = MouseInputTargetType.Any
    cameraSettings.sendMouseMotion = true
    cameraSettings.allowPitchControls = true
    cameraSettings.displayReticle = false
    cameraSettings.displayCursor = false
    playerRef.packetHandler.writeNoCache(SetServerCamera(ClientCameraView.Custom, false, cameraSettings))
}

fun cameraReset(playerRef: PlayerRef) {
    playerRef.packetHandler.writeNoCache(SetServerCamera(ClientCameraView.Custom, false, null))
}

package com.kejing.spatialxiangqi.ui.board.components

import android.content.Context
import android.util.Log
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.domain.model.Piece
import com.kejing.spatialxiangqi.domain.model.PieceFace
import com.kejing.spatialxiangqi.domain.model.PieceType
import com.kejing.spatialxiangqi.domain.model.Side
import com.kejing.spatialxiangqi.domain.model.Skin
import com.kejing.spatialxiangqi.domain.model.Square
import com.kejing.spatialxiangqi.ui.board.BoardUiState
import com.kejing.spatialxiangqi.ui.board.Phase
import com.kejing.spatialxiangqi.ui.theme.SkinSpec
import com.kejing.spatialxiangqi.ui.theme.Skins
import com.kejing.spatialxiangqi.ui.theme.toColor4
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp

/**
 * Runtime knobs for things that can only be confirmed on a device / emulator (axis conventions of
 * procedural meshes, texture V direction, front of the volume). Adjustable through the adb debug
 * channel so the layout can be iterated without rebuilding.
 */
class SceneTuning {
    var wallPitchDeg = 90f          // rotation that turns the flat board into a hanging board
    var planePitchDeg = -90f        // createPlane is vertical (XY); -90° lays it flat facing +Y (verified on emulator)
    var faceFlipX = false           // mirror board/piece textures horizontally
    var faceFlipY = false           // mirror board/piece textures vertically
    var frontZ = 0.36f              // z of the hanging board face (wall mode), + toward the viewer
    var wallY = -0.03f              // vertical centre of the hanging board (leaves room for the status plaque)
    var tableY = -0.50f             // board slab centre in table mode (just above the volume floor)
    var tableZ = 0.0f
    var pieceLift = 1.0f            // multiplier of the selected-piece lift
    var statusOffset = 0.05f
    var statusScale = 1.8f
    fun apply(key: String, value: String): Boolean {
        val f = value.toFloatOrNull()
        when (key) {
            "wallPitch" -> wallPitchDeg = f ?: return false
            "planePitch" -> planePitchDeg = f ?: return false
            "flipX" -> faceFlipX = value == "1" || value == "true"
            "flipY" -> faceFlipY = value == "1" || value == "true"
            "frontZ" -> frontZ = f ?: return false
            "wallY" -> wallY = f ?: return false
            "tableY" -> tableY = f ?: return false
            "tableZ" -> tableZ = f ?: return false
            "lift" -> pieceLift = f ?: return false
            "status" -> statusOffset = f ?: return false
            "statusScale" -> statusScale = f ?: return false
            else -> return false
        }
        return true
    }
}

/** Geometry derived from the pitch (distance between intersections). All lengths in meters. */
data class BoardGeometry(val pitch: Float) {
    val width = pitch * 10f
    val height = pitch * 11f
    val thickness = pitch * 0.34f
    val pieceRadius = pitch * 0.38f
    val pieceHeight = pitch * 0.24f
    val frameInset = pitch * 0.32f
    fun x(file: Int) = (file - 4) * pitch
    fun z(rank: Int) = (4.5f - rank) * pitch
    val top get() = thickness / 2f
}

/**
 * Owns the ECS entities of the board: slab, face texture, ledge, eval rail, 32 pieces, 90
 * tap pads and a pool of highlight marks. Everything is procedural (no external assets) so
 * skins and glyph modes can be switched at runtime. Rendering is a pure function of
 * [BoardUiState]: [sync] diffs the previous state and only touches what changed.
 */
class BoardScene(context: Context, val tuning: SceneTuning = SceneTuning()) {
    val root = Entity().apply { setName("XiangqiRoot") }
    private var boardRoot = Entity().apply { setName("BoardRoot") }
    private val textures = BoardTextures(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Pair<BoardUiState, Int>? = null
    private var syncJob: Job? = null

    /** Every SDK resource (blocking runtime round-trips) is created off the main thread here. */
    private class Resources(
        val physics: PhysicsMaterialResource,
        val frameMesh: MeshResource, val slabMesh: MeshResource, val faceMesh: MeshResource, val ledgeMesh: MeshResource,
        val railMesh: MeshResource, val bodyMesh: MeshResource, val rimMesh: MeshResource, val pieceFaceMesh: MeshResource,
        val dotMesh: MeshResource, val ringMesh: MeshResource, val frameMarkMesh: MeshResource, val accentMesh: MeshResource,
        val padShape: ShapeResource, val pieceShape: ShapeResource,
        val frameMat: Material, val slabMat: Material, val faceMat: Material, val ledgeMat: Material,
        val railRedMat: Material, val railBlackMat: Material, val bodyMat: Material, val rimMat: Material,
        val pieceFaceMats: Map<Piece, Material>,
        val dotMat: UnlitMaterial, val ringMat: UnlitMaterial, val frameFromMat: UnlitMaterial, val frameToMat: UnlitMaterial, val checkMat: UnlitMaterial,
    )

    private class PieceNode(val piece: Piece, val holder: Entity, val body: ModelEntity, val face: ModelEntity) { var square: Square? = null }
    private class Mark(val entity: ModelEntity, val material: UnlitMaterial)

    private val pieceNodes = ArrayList<PieceNode>(32)
    private val entityToSquare = HashMap<Entity, Square>()
    private val pieceByEntity = HashMap<Entity, PieceNode>()
    private val pads = HashMap<Square, Entity>()
    private var dots = ArrayList<Mark>()
    private var rings = ArrayList<Mark>()
    private var frames = ArrayList<Mark>()
    private var accents = ArrayList<Mark>()   // check ring, selection ring, hint rings
    private var evalRed: ModelEntity? = null
    private var evalBlack: ModelEntity? = null
    private var ledge: ModelEntity? = null
    private var statusEntity: Entity? = null

    private var geometry = BoardGeometry(0.08f)
    private var builtKey: String? = null
    private var attached = false
    private var lastState: BoardUiState? = null
    private var lastTuningStamp = 0

    fun attach(content: SpatialViewContent) {
        if (attached) return
        content.addEntity(root)
        attached = true
    }

    fun setStatusEntity(entity: Entity?) { statusEntity = entity }

    fun squareFor(entity: Entity?): Square? {
        if (entity == null) return null
        pieceByEntity[entity]?.let { return it.square }
        return entityToSquare[entity]
    }

    /** Pitch for the current mode and user scale, clamped to the volume envelope. */
    fun pitchFor(state: BoardUiState): Float {
        val base = if (state.mode == BoardMode.WALL) 0.08f else 0.06f
        val wanted = base * state.settings.boardScalePercent / 100f
        val maxByWidth = (VOLUME_W - 0.06f) / 10f
        val maxByDepth = if (state.mode == BoardMode.WALL) Float.MAX_VALUE else (VOLUME_D - 0.10f) / 11f
        val maxByHeight = if (state.mode == BoardMode.WALL) (VOLUME_H - 0.10f) / 11f else Float.MAX_VALUE
        return minOf(wanted, maxByWidth, maxByDepth, maxByHeight)
    }

    /** Coalesces state updates; heavy work never blocks the caller (main thread). */
    fun requestSync(state: BoardUiState, tuningStamp: Int = 0) {
        pending = state to tuningStamp
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (true) {
                val (s, stamp) = pending ?: break
                pending = null
                runCatching { doSync(s, stamp) }.onFailure { Log.e(TAG, "sync failed", it) }
            }
        }
    }

    private suspend fun doSync(state: BoardUiState, tuningStamp: Int) {
        val pitch = pitchFor(state)
        val key = listOf(state.skin, state.pieceFace, state.settings.showCoordinates, "%.4f".format(pitch), tuning.planePitchDeg, tuning.faceFlipX, tuning.faceFlipY).joinToString("|")
        if (key != builtKey) {
            val t0 = SystemClock.elapsedRealtime()
            val res = withContext(Dispatchers.IO) { createResources(state, pitch) }
            val t1 = SystemClock.elapsedRealtime()
            rebuild(state, pitch, res)
            Log.i(TAG, "SCENE_BUILT pitch=$pitch resources=${t1 - t0}ms entities=${SystemClock.elapsedRealtime() - t1}ms skin=${state.skin} face=${state.pieceFace}")
            builtKey = key
            lastState = null
        }
        val prev = lastState
        if (prev == null || prev.mode != state.mode || prev.settings != state.settings || tuningStamp != lastTuningStamp) {
            placeBoardRoot(state)
            placeStatus(state)
            lastTuningStamp = tuningStamp
        }
        if (prev == null || prev.position != state.position || prev.selected != state.selected || prev.phase != state.phase) assignPieces(state)
        if (prev == null || prev.selected != state.selected || prev.legalTargets != state.legalTargets || prev.captureTargets != state.captureTargets ||
            prev.lastMove != state.lastMove || prev.hintMove != state.hintMove || prev.checkedKing != state.checkedKing || prev.position != state.position || prev.phase != state.phase
        ) updateMarks(state)
        if (prev == null || prev.evalCp != state.evalCp || prev.phase != state.phase) updateEval(state)
        lastState = state
    }

    // ------------------------------------------------------------------ build

    private inline fun <T> timed(label: String, block: () -> T): T {
        val t = SystemClock.elapsedRealtime()
        val r = block()
        Log.i(TAG, "res $label ${SystemClock.elapsedRealtime() - t}ms")
        return r
    }

    private suspend fun createResources(state: BoardUiState, pitch: Float): Resources = coroutineScope {
        val skin = Skins.of(state.skin)
        val g = BoardGeometry(pitch)
        val white = Color4(1f, 1f, 1f, 1f)
        val t0 = SystemClock.elapsedRealtime()
        // Textures and meshes are blocking runtime round-trips (~1 s each when serialized); issue them concurrently.
        val boardTexD = async(Dispatchers.IO) { timed("boardTexture") { TextureResource.create(flip(textures.boardFace(skin, state.settings.showCoordinates, 1024))) } }
        val pieceTexD = Side.entries.flatMap { side -> PieceType.entries.map { type -> Piece.of(side, type) } }.map { piece ->
            piece to async(Dispatchers.IO) { timed("pieceTex ${piece.glyph}") { TextureResource.create(flip(textures.pieceFace(piece, skin, state.pieceFace, 256))) } }
        }
        val markTexD = listOf(
            async(Dispatchers.IO) { timed("dotTex") { TextureResource.create(textures.disc(Skins.AMBER, 230, 96)) } },
            async(Dispatchers.IO) { timed("ringTex") { TextureResource.create(textures.disc(Skins.AMBER, 235, 128, ring = true)) } },
            async(Dispatchers.IO) { timed("frameFromTex") { TextureResource.create(textures.frame(Skins.AMBER, dashed = true)) } },
            async(Dispatchers.IO) { timed("frameToTex") { TextureResource.create(textures.frame(Skins.AMBER, dashed = false)) } },
            async(Dispatchers.IO) { timed("checkTex") { TextureResource.create(textures.disc(0xFFD64545.toInt(), 235, 128, ring = true)) } },
        )
        val meshD = listOf(
            async(Dispatchers.IO) { timed("frameMesh") { MeshResource.createBox(Vector3(g.width + g.frameInset, g.thickness * 0.75f, g.height + g.frameInset), 0f) } },
            async(Dispatchers.IO) { timed("slabMesh") { MeshResource.createBox(Vector3(g.width, g.thickness, g.height), 0f) } },
            async(Dispatchers.IO) { timed("faceMesh") { MeshResource.createPlane(g.width - 0.004f, g.height - 0.004f, 0f) } },
            async(Dispatchers.IO) { timed("ledgeMesh") { MeshResource.createBox(Vector3(g.width + g.frameInset, 0.05f, 0.02f), 0f) } },
            async(Dispatchers.IO) { timed("railMesh") { MeshResource.createBox(Vector3(0.012f, 0.008f, 1f), 0f) } },
            async(Dispatchers.IO) { timed("bodyMesh") { MeshResource.createCylinder(g.pieceHeight, g.pieceRadius) } },
            async(Dispatchers.IO) { timed("rimMesh") { MeshResource.createCylinder(g.pieceHeight * 0.35f, g.pieceRadius * 1.02f) } },
            async(Dispatchers.IO) { timed("pieceFaceMesh") { MeshResource.createPlane(g.pieceRadius * 2f, g.pieceRadius * 2f, 0f) } },
            async(Dispatchers.IO) { timed("dotMesh") { MeshResource.createPlane(g.pitch * 0.30f, g.pitch * 0.30f, 0f) } },
            async(Dispatchers.IO) { timed("ringMesh") { MeshResource.createPlane(g.pitch * 1.15f, g.pitch * 1.15f, 0f) } },
            async(Dispatchers.IO) { timed("frameMarkMesh") { MeshResource.createPlane(g.pitch * 0.96f, g.pitch * 0.96f, 0f) } },
            async(Dispatchers.IO) { timed("accentMesh") { MeshResource.createPlane(g.pitch * 1.34f, g.pitch * 1.34f, 0f) } },
        )
        val meshes = meshD.awaitAll()
        val boardTex = boardTexD.await()
        val markTex = markTexD.awaitAll()
        val faceMat = timed("faceMat") {
            PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(white); setRoughness(0.6f); setMetallic(0f)
                setBaseColorTexture(boardTex)
            }
        }
        val pieceFaceMats = HashMap<Piece, Material>()
        for ((piece, d) in pieceTexD) {
            val tex = d.await()
            pieceFaceMats[piece] = PhysicallyBasedMaterial.create(BlendingMode.MASKED).apply {
                setBaseColor(white); setRoughness(0.5f); setMetallic(0f); setOpacityThreshold(0.5f)
                setBaseColorTexture(tex)
            }
        }
        Log.i(TAG, "resources (parallel) ${SystemClock.elapsedRealtime() - t0}ms")
        fun markMat(tex: TextureResource) = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply { setBaseColor(white); setBaseColorTexture(tex) }
        Resources(
            physics = timed("physics") { PhysicsMaterialResource(0.6f, 0.5f, 0f) },
            frameMesh = meshes[0],
            slabMesh = meshes[1],
            faceMesh = meshes[2],
            ledgeMesh = meshes[3],
            railMesh = meshes[4],
            bodyMesh = meshes[5],
            rimMesh = meshes[6],
            pieceFaceMesh = meshes[7],
            dotMesh = meshes[8],
            ringMesh = meshes[9],
            frameMarkMesh = meshes[10],
            accentMesh = meshes[11],
            padShape = timed("padShape") { ShapeResource.createBox(Vector3(g.pitch * 0.92f, 0.004f, g.pitch * 0.92f)) },
            pieceShape = timed("pieceShape") { ShapeResource.createBox(Vector3(g.pieceRadius * 2f, g.pieceHeight, g.pieceRadius * 2f)) },
            frameMat = timed("frameMat") { pbr(skin.ledge4, roughness = 0.7f) },
            slabMat = timed("slabMat") { pbr(skin.boardFace4, roughness = 0.55f) },
            faceMat = faceMat,
            ledgeMat = timed("ledgeMat") { pbr(skin.ledge4, roughness = 0.6f, metallic = if (skin.brassInlay) 0.15f else 0f) },
            railRedMat = timed("railRedMat") { unlit(Skins.CINNABAR.toColor4()) },
            railBlackMat = timed("railBlackMat") { unlit(Skins.INK.toColor4()) },
            bodyMat = timed("bodyMat") { pbr(skin.pieceFace4, roughness = 0.45f) },
            rimMat = timed("rimMat") { pbr(skin.pieceRim4, roughness = 0.5f) },
            pieceFaceMats = pieceFaceMats,
            dotMat = markMat(markTex[0]),
            ringMat = markMat(markTex[1]),
            frameFromMat = markMat(markTex[2]),
            frameToMat = markMat(markTex[3]),
            checkMat = markMat(markTex[4]),
        )
    }

    /** Entity graph only (main thread); every resource comes pre-built from [createResources]. */
    private fun rebuild(state: BoardUiState, pitch: Float, r: Resources) {
        geometry = BoardGeometry(pitch)
        val g = geometry
        val old = boardRoot
        root.removeChild(old)
        runCatching { old.destroy(true) }.onFailure { Log.w(TAG, "destroy old board failed", it) }
        pieceNodes.clear(); entityToSquare.clear(); pieceByEntity.clear(); pads.clear()
        dots = ArrayList(); rings = ArrayList(); frames = ArrayList(); accents = ArrayList()
        boardRoot = Entity().apply { setName("BoardRoot") }
        root.addChild(boardRoot)

        val frame = ModelEntity(r.frameMesh, r.frameMat)
        frame.transform().setPosition(Vector3(0f, -g.thickness * 0.2f, 0f))
        boardRoot.addChild(frame)
        boardRoot.addChild(ModelEntity(r.slabMesh, r.slabMat))
        val face = ModelEntity(r.faceMesh, r.faceMat)
        face.transform().setPosition(Vector3(0f, g.top + 0.0006f, 0f)).setEulerAngles(EulerAngles(tuning.planePitchDeg, 0f, 0f))
        boardRoot.addChild(face)

        ledge = ModelEntity(r.ledgeMesh, r.ledgeMat).also {
            it.transform().setPosition(Vector3(0f, g.top + 0.02f, g.height / 2f + g.frameInset / 2f + 0.01f))
            boardRoot.addChild(it)
        }

        val railLen = g.pitch * 9f
        evalRed = ModelEntity(r.railMesh, r.railRedMat).also { boardRoot.addChild(it); it.transform().setScaleVector(Vector3(1f, 1f, railLen * 0.5f)) }
        evalBlack = ModelEntity(r.railMesh, r.railBlackMat).also { boardRoot.addChild(it); it.transform().setScaleVector(Vector3(1f, 1f, railLen * 0.5f)) }

        for (sq in Square.ALL) {
            val pad = Entity().apply {
                setName("Pad_${sq.iccs}")
                transform().setPosition(Vector3(g.x(sq.file), g.top + 0.002f, g.z(sq.rank)))
                components.set(CollisionComponent(listOf(r.padShape), r.physics))
                components.set(InteractableComponent())
            }
            boardRoot.addChild(pad)
            pads[sq] = pad
            entityToSquare[pad] = sq
        }

        for (side in Side.entries) for (type in PieceType.entries) repeat(type.maxCount) {
            val piece = Piece.of(side, type)
            val holder = Entity().apply {
                setName("Piece_${side.name}_${type.name}")
                components.set(CollisionComponent(listOf(r.pieceShape), r.physics))
                components.set(InteractableComponent())
                components.set(HoverEffectComponent())
            }
            val body = ModelEntity(r.bodyMesh, r.bodyMat)
            val rim = ModelEntity(r.rimMesh, r.rimMat)
            rim.transform().setPosition(Vector3(0f, -g.pieceHeight * 0.325f, 0f))
            val face2 = ModelEntity(r.pieceFaceMesh, r.pieceFaceMats.getValue(piece))
            face2.transform().setPosition(Vector3(0f, g.pieceHeight / 2f + 0.0006f, 0f)).setEulerAngles(EulerAngles(tuning.planePitchDeg, 0f, 0f))
            holder.addChild(body); holder.addChild(rim); holder.addChild(face2)
            holder.enabled = false
            boardRoot.addChild(holder)
            val node = PieceNode(piece, holder, body, face2)
            pieceNodes += node
            pieceByEntity[holder] = node; pieceByEntity[body] = node; pieceByEntity[face2] = node; pieceByEntity[rim] = node
        }

        repeat(20) { dots += mark(r.dotMesh, r.dotMat) }
        repeat(20) { rings += mark(r.ringMesh, r.ringMat) }
        frames += mark(r.frameMarkMesh, r.frameFromMat); frames += mark(r.frameMarkMesh, r.frameToMat)
        accents += mark(r.accentMesh, r.checkMat)   // 0: check
        accents += mark(r.accentMesh, r.ringMat)    // 1: selection
        accents += mark(r.accentMesh, r.ringMat)    // 2: hint from
        accents += mark(r.accentMesh, r.ringMat)    // 3: hint to
    }

    private fun mark(mesh: MeshResource, mat: UnlitMaterial): Mark {
        val e = ModelEntity(mesh, mat)
        e.transform().setEulerAngles(EulerAngles(tuning.planePitchDeg, 0f, 0f))
        e.enabled = false
        boardRoot.addChild(e)
        return Mark(e, mat)
    }

    private fun pbr(color: Color4, roughness: Float, metallic: Float = 0f): Material =
        PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply { setBaseColor(color); setRoughness(roughness); setMetallic(metallic) }

    private fun unlit(color: Color4): Material = UnlitMaterial.create(BlendingMode.OPAQUE).apply { setBaseColor(color) }

    private fun flip(bmp: android.graphics.Bitmap): android.graphics.Bitmap {
        if (!tuning.faceFlipX && !tuning.faceFlipY) return bmp
        val m = android.graphics.Matrix().apply { preScale(if (tuning.faceFlipX) -1f else 1f, if (tuning.faceFlipY) -1f else 1f) }
        return android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    // ------------------------------------------------------------------ placement

    private fun placeBoardRoot(state: BoardUiState) {
        val g = geometry
        val trim = state.settings.heightTrimCm / 100f
        val t = boardRoot.transform()
        if (state.mode == BoardMode.WALL) {
            t.setPosition(Vector3(0f, tuning.wallY + trim, tuning.frontZ - g.top))
            t.setEulerAngles(EulerAngles(tuning.wallPitchDeg, 0f, 0f))
            ledge?.enabled = true
        } else {
            // Positive pitch drops the near edge and lifts the far (opponent's) edge, the way a reading
            // stand tilts toward the player; lift the slab so the near edge stays on the volume floor.
            val tilt = state.settings.tiltDeg.toFloat()
            val lift = (g.height / 2f) * Math.sin(Math.toRadians(tilt.toDouble())).toFloat()
            t.setPosition(Vector3(0f, tuning.tableY + g.thickness * 0.5f + trim + lift, tuning.tableZ))
            t.setEulerAngles(EulerAngles(tilt, state.settings.orientationDeg.toFloat(), 0f))
            ledge?.enabled = false
        }
    }

    private fun placeStatus(state: BoardUiState) {
        val e = statusEntity ?: return
        val g = geometry
        val trim = state.settings.heightTrimCm / 100f
        val t = e.transform()
        t.setScaleVector(Vector3(tuning.statusScale, tuning.statusScale, tuning.statusScale))
        if (state.mode == BoardMode.WALL) {
            t.setPosition(Vector3(0f, tuning.wallY + trim + g.height / 2f + tuning.statusOffset + 0.03f, tuning.frontZ + 0.01f))
            t.setEulerAngles(EulerAngles(0f, 0f, 0f))
        } else {
            t.setPosition(Vector3(0f, tuning.tableY + g.thickness + 0.10f + trim, tuning.tableZ - g.height / 2f - tuning.statusOffset))
            t.setEulerAngles(EulerAngles(0f, 0f, 0f))
        }
    }

    private fun assignPieces(state: BoardUiState) {
        val g = geometry
        val desired = state.position.pieces
        val used = HashSet<PieceNode>()
        val pending = ArrayList<Pair<Square, Piece>>()
        for ((sq, piece) in desired) {
            val node = pieceNodes.firstOrNull { it.piece == piece && it.square == sq }
            if (node != null) used += node else pending += sq to piece
        }
        for ((sq, piece) in pending) {
            val node = pieceNodes.firstOrNull { it.piece == piece && it !in used } ?: continue
            node.square = sq
            used += node
        }
        for (node in pieceNodes) {
            if (node !in used) { node.square = null; node.holder.enabled = false; continue }
            val sq = node.square ?: continue
            val lift = if (state.selected == sq) g.pieceHeight * 0.6f * tuning.pieceLift else 0f
            node.holder.transform().setPosition(Vector3(g.x(sq.file), g.top + g.pieceHeight / 2f + lift, g.z(sq.rank)))
            node.holder.enabled = true
        }
    }

    private fun updateMarks(state: BoardUiState) {
        val g = geometry
        val y = g.top + 0.0012f
        fun place(m: Mark, sq: Square?) {
            if (sq == null) { m.entity.enabled = false; return }
            m.entity.transform().setPosition(Vector3(g.x(sq.file), y, g.z(sq.rank)))
            m.entity.enabled = true
        }
        val showMoves = state.phase != Phase.SETUP
        val captures = state.captureTargets.toSet()
        val plain = state.legalTargets.filter { it !in captures }
        dots.forEachIndexed { i, m -> place(m, if (showMoves) plain.getOrNull(i) else null) }
        rings.forEachIndexed { i, m -> place(m, if (showMoves) state.captureTargets.getOrNull(i) else null) }
        place(frames[0], if (showMoves) state.lastMove?.from else null)
        place(frames[1], if (showMoves) state.lastMove?.to else null)
        place(accents[0], if (showMoves) state.checkedKing else null)
        place(accents[1], if (showMoves) state.selected else null)
        place(accents[2], if (showMoves) state.hintMove?.from else null)
        place(accents[3], if (showMoves) state.hintMove?.to else null)
    }

    private fun updateEval(state: BoardUiState) {
        val g = geometry
        val red = evalRed ?: return
        val black = evalBlack ?: return
        val visible = state.phase != Phase.SETUP
        red.enabled = visible; black.enabled = visible
        val len = g.pitch * 9f
        val f = (1f / (1f + exp(-state.evalCp / 400f))).coerceIn(0.04f, 0.96f)
        val x = -g.width / 2f - 0.022f
        red.transform().setScaleVector(Vector3(1f, 1f, len * f)).setPosition(Vector3(x, g.top - 0.004f, len / 2f - len * f / 2f))
        black.transform().setScaleVector(Vector3(1f, 1f, len * (1f - f))).setPosition(Vector3(x, g.top - 0.004f, -len / 2f + len * (1f - f) / 2f))
    }

    private fun Entity.transform(): TransformComponent =
        components.get<TransformComponent>() ?: TransformComponent().also { components.set(it) }

    companion object {
        const val TAG = "XiangqiScene"
        const val VOLUME_W = 0.96f
        const val VOLUME_H = 1.05f
        const val VOLUME_D = 0.9f
    }
}

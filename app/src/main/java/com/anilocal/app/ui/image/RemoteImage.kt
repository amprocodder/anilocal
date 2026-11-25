package com.anilocal.app.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An image from a URL, drawn centred with [contentScale] — the app's replacement for Coil's
 * `AsyncImage`. Nothing is drawn until it has loaded (there's no placeholder, as before), and a
 * failed load just stays empty.
 *
 * It **fills the size its modifier gives it**: every call site sizes it (`size`, `fillMaxSize`,
 * `aspectRatio`…), so it never measures the image. Loading and drawing live in one modifier node,
 * so an image arriving redraws that node only — no recomposition — and leaving the screen cancels
 * the load.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
) {
    val loader = (LocalContext.current.applicationContext as ImageLoader.Provider).imageLoader
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Layout(
        modifier = modifier.then(semantics).then(RemoteImageElement(url, contentScale, alpha, loader)),
        measurePolicy = FillConstraints,
    )
}

private val FillConstraints = MeasurePolicy { _, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
    layout(width, height) {}
}

private data class RemoteImageElement(
    val url: String?,
    val contentScale: ContentScale,
    val alpha: Float,
    val loader: ImageLoader,
) : ModifierNodeElement<RemoteImageNode>() {
    override fun create() = RemoteImageNode(url, contentScale, alpha, loader)
    override fun update(node: RemoteImageNode) = node.update(url, contentScale, alpha, loader)
}

private class RemoteImageNode(
    private var url: String?,
    private var contentScale: ContentScale,
    private var alpha: Float,
    private var loader: ImageLoader,
) : Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private var size = IntSize.Zero
    private var image: ImageBitmap? = null
    /** The size the current image (or the load in flight) was requested at. */
    private var requested = IntSize.Zero
    private var job: Job? = null

    fun update(url: String?, contentScale: ContentScale, alpha: Float, loader: ImageLoader) {
        val sourceChanged = url != this.url || loader !== this.loader
        val scaleChanged = contentScale != this.contentScale
        val alphaChanged = alpha != this.alpha
        this.url = url
        this.contentScale = contentScale
        this.alpha = alpha
        this.loader = loader
        if (sourceChanged) {
            image = null
            cancelLoad()
        }
        if (sourceChanged || scaleChanged) request()
        if (sourceChanged || scaleChanged || alphaChanged) invalidateDraw()
    }

    override fun onRemeasured(size: IntSize) {
        if (size == this.size) return
        this.size = size
        request()
    }

    override fun onAttach() {
        request()
    }

    override fun onDetach() {
        cancelLoad()
    }

    /**
     * A lazy list reusing this node for another item. The size is kept: a reused item with the same
     * constraints isn't remeasured, so [onRemeasured] won't report it again and [update]'s request
     * needs it.
     */
    override fun onReset() {
        image = null
        cancelLoad()
    }

    private fun cancelLoad() {
        job?.cancel()
        job = null
        requested = IntSize.Zero
    }

    /**
     * Takes a memory hit at once, so a cached image draws in this very frame; otherwise loads in the
     * node's scope. A load already running for at least this size is left alone.
     */
    private fun request() {
        val url = url ?: return
        if (!isAttached || size.width <= 0 || size.height <= 0) return
        if (image != null && requested.covers(size)) return
        if (job?.isActive == true && requested.covers(size)) return
        loader.peek(url, size, contentScale)?.let {
            image = it
            requested = size
            invalidateDraw()
            return
        }
        job?.cancel()
        requested = size
        val target = size
        val scale = contentScale
        job = coroutineScope.launch {
            val loaded = loader.load(url, target, scale) ?: return@launch
            if (url == this@RemoteImageNode.url) {
                image = loaded
                invalidateDraw()
            }
        }
    }

    private fun IntSize.covers(other: IntSize) = width >= other.width && height >= other.height

    /**
     * Draws only the part of the image that lands inside the bounds (as `Image` + `clipToBounds`
     * would show), by mapping the visible destination back to a source rectangle — no clip needed.
     */
    override fun ContentDrawScope.draw() {
        val image = image ?: return
        val dst = this.size
        if (dst.width <= 0f || dst.height <= 0f) return
        val src = Size(image.width.toFloat(), image.height.toFloat())
        val factor = contentScale.computeScaleFactor(src, dst)
        val scaledW = src.width * factor.scaleX
        val scaledH = src.height * factor.scaleY
        val offset = Alignment.Center.align(
            IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
            IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
            layoutDirection,
        )
        val left = max(0f, offset.x.toFloat())
        val top = max(0f, offset.y.toFloat())
        val right = min(dst.width, offset.x + scaledW)
        val bottom = min(dst.height, offset.y + scaledH)
        if (right <= left || bottom <= top) return
        val srcLeft = ((left - offset.x) / factor.scaleX).roundToInt().coerceIn(0, image.width - 1)
        val srcTop = ((top - offset.y) / factor.scaleY).roundToInt().coerceIn(0, image.height - 1)
        val srcRight = ((right - offset.x) / factor.scaleX).roundToInt().coerceIn(srcLeft + 1, image.width)
        val srcBottom = ((bottom - offset.y) / factor.scaleY).roundToInt().coerceIn(srcTop + 1, image.height)
        drawImage(
            image = image,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize((right - left).roundToInt(), (bottom - top).roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
}

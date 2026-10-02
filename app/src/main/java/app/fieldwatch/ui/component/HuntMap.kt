package app.fieldwatch.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import app.fieldwatch.UiText
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.*
import kotlin.math.*

/** North-up local map; every target overlay is a model hypothesis, separate from measured receiver points. */
@Composable
fun HuntMap(points: List<HuntGeoPoint>, fix: HuntFix?, result: HuntPosition, online: Boolean,
    modifier: Modifier = Modifier, heading: HuntHeading? = null) {
    val context = LocalContext.current
    var tiles by remember { mutableStateOf(emptyList<PathTiles.Tile>()) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<HuntGeoPoint?>(null) }
    val coordinates = remember(points, fix, result) {
        points.map { HuntCandidate(it.fix.lat, it.fix.lon) } + listOfNotNull(fix?.let { HuntCandidate(it.lat, it.lon) }) +
            result.candidates + listOfNotNull(result.center)
    }
    if (coordinates.isEmpty()) return
    val bounds = remember(coordinates) {
        PathTiles.expandToPlot(coordinates.minOf { it.lat }, coordinates.maxOf { it.lat },
            coordinates.minOf { it.lon }, coordinates.maxOf { it.lon }, 1.7)
    }
    // Debounce moving GPS/advertisement updates; keep drawing and scanning independent of tile loading.
    LaunchedEffect(bounds.map { floor(it * 1_000).toInt() }, online) {
        tiles = PathTiles.load(context, listOf(GpsSample(0, bounds[0], bounds[2]),
            GpsSample(0, bounds[1], bounds[3])), online)
    }
    val colors = MaterialTheme.colorScheme
    val strongest = points.maxByOrNull { it.signal }
    val trueHeading = remember(fix, heading) {
        if (fix == null || heading == null) null else heading.degrees + android.hardware.GeomagneticField(
            fix.lat.toFloat(), fix.lon.toFloat(), 0f, fix.at).declination
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f).clipToBounds()
            .pointerInput(Unit) { detectTransformGestures { _, delta, scale, _ ->
                zoom = (zoom * scale).coerceIn(1f, 8f); pan += delta
            } }
            .pointerInput(points, bounds.toList(), zoom, pan) {
                detectTapGestures { tap ->
                    fun screen(point: HuntGeoPoint): Offset {
                        val x = ((point.fix.lon - bounds[2]) / (bounds[3] - bounds[2])).toFloat() * size.width
                        val y = ((bounds[1] - point.fix.lat) / (bounds[1] - bounds[0])).toFloat() * size.height
                        return (Offset(x, y) - Offset(size.width / 2f, size.height / 2f)) * zoom +
                            Offset(size.width / 2f, size.height / 2f) + pan
                    }
                    selected = points.minByOrNull { (screen(it) - tap).getDistance() }
                        ?.takeIf { (screen(it) - tap).getDistance() < 32.dp.toPx() }
                }
            }) {
            drawRect(colors.surfaceVariant)
            fun xy(lat: Double, lon: Double): Offset {
                val p = Offset(((lon - bounds[2]) / (bounds[3] - bounds[2]) * size.width).toFloat(),
                    ((bounds[1] - lat) / (bounds[1] - bounds[0]) * size.height).toFloat())
                return (p - center) * zoom + center + pan
            }
            for (tile in tiles) {
                val top = xy(tile.north, tile.west); val bottom = xy(tile.south, tile.east)
                drawImage(tile.bitmap.asImageBitmap(), dstOffset = IntOffset(top.x.roundToInt(), top.y.roundToInt()),
                    dstSize = IntSize((bottom.x - top.x).roundToInt().coerceAtLeast(1),
                        (bottom.y - top.y).roundToInt().coerceAtLeast(1)))
            }
            val metersPerPixel = (bounds[1] - bounds[0]) * 110_540 / size.height / zoom
            result.candidates.forEach {
                val top = xy(it.lat + result.cellLatStep / 2, it.lon - result.cellLonStep / 2)
                val bottom = xy(it.lat - result.cellLatStep / 2, it.lon + result.cellLonStep / 2)
                drawRect(colors.tertiary.copy(alpha = 0.2f), top, Size(bottom.x - top.x, bottom.y - top.y))
            }
            points.zipWithNext().forEach { (a, b) -> drawLine(colors.primary.copy(alpha = 0.5f),
                xy(a.fix.lat, a.fix.lon), xy(b.fix.lat, b.fix.lon), 2.dp.toPx()) }
            points.forEach { point ->
                val p = xy(point.fix.lat, point.fix.lon)
                val strength = ((point.signal + 100) / 60).coerceIn(0.15, 1.0).toFloat()
                drawCircle(colors.primary.copy(alpha = 0.09f), (point.fix.accuracy / metersPerPixel).toFloat(), p)
                drawCircle(colors.primary.copy(alpha = strength), 5.dp.toPx(), p)
                if (point == strongest) drawCircle(colors.onSurface, 8.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
            result.center?.let {
                val p = xy(it.lat, it.lon)
                drawCircle(colors.tertiary.copy(alpha = 0.25f), (result.radius / metersPerPixel).toFloat(), p,
                    style = Stroke(1.dp.toPx()))
                drawCircle(colors.tertiary, 12.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                drawLine(colors.tertiary, p - Offset(17.dp.toPx(), 0f), p + Offset(17.dp.toPx(), 0f), 2.dp.toPx())
                drawLine(colors.tertiary, p - Offset(0f, 17.dp.toPx()), p + Offset(0f, 17.dp.toPx()), 2.dp.toPx())
            }
            fix?.let {
                val p = xy(it.lat, it.lon)
                drawCircle(colors.secondary, 7.dp.toPx(), p)
                trueHeading?.let { degrees ->
                    val angle = Math.toRadians(degrees.toDouble())
                    val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                    val sideways = Offset(direction.y, -direction.x)
                    val tip = p + direction * 23.dp.toPx()
                    val arrowColor = if (heading?.reliable == true) colors.secondary else colors.outline
                    drawLine(arrowColor, p, tip, 3.dp.toPx())
                    drawLine(arrowColor, tip, tip - direction * 9.dp.toPx() + sideways * 6.dp.toPx(), 3.dp.toPx())
                    drawLine(arrowColor, tip, tip - direction * 9.dp.toPx() - sideways * 6.dp.toPx(), 3.dp.toPx())
                }
            }
            drawLine(colors.onSurface, Offset(12.dp.toPx(), size.height - 12.dp.toPx()),
                Offset(12.dp.toPx() + (20 / metersPerPixel).toFloat(), size.height - 12.dp.toPx()), 2.dp.toPx())
        }
        Text(UiText.text(R.string.hunt_map_legend), style = MaterialTheme.typography.labelSmall)
        Text(UiText.text(if (tiles.isEmpty()) R.string.hunt_map_offline else R.string.hunt_map_attribution),
            style = MaterialTheme.typography.labelSmall)
        selected?.let { chosen -> points.firstOrNull { it.fix.at == chosen.fix.at } }?.let { Text(UiText.text(R.string.hunt_map_point, "%.1f".format(java.util.Locale.US, it.signal),
            it.readings.size, "%.0f".format(java.util.Locale.US, it.fix.accuracy),
            it.heading?.let { heading -> "${heading.degrees}°" +
                if (heading.reliable) "" else " · " + UiText.text(R.string.hunt_heading_low_accuracy) } ?: "—"),
            style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text(UiText.text(R.string.hunt_map_recenter)) }
    }
}

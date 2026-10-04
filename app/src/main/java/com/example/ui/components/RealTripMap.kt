package com.example.ui.components

import android.graphics.BitmapFactory
import android.location.Geocoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LatLngPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

private const val TILE_SIZE = 256
private const val MAX_ZOOM = 16
private const val MIN_ZOOM = 11

private val tileClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}

private val tileCache = object : LinkedHashMap<String, ImageBitmap>(48, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 64
}

/**
 * Static real-map preview for a trip card. Tiles come from OpenStreetMap,
 * with the recorded GPS route drawn on top.
 */
@Composable
fun RealTripMap(
    pathJson: String,
    startAddress: String?,
    endAddress: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorded = remember(pathJson) { parseRoutePoints(pathJson) }
    var points by remember(pathJson, startAddress, endAddress) { mutableStateOf(recorded) }

    LaunchedEffect(pathJson, startAddress, endAddress) {
        if (recorded.size >= 2) {
            points = recorded
            return@LaunchedEffect
        }
        val geocoded = withContext(Dispatchers.IO) {
            geocodeEndpoints(context, startAddress, endAddress)
        }
        if (geocoded.size >= 2) points = geocoded
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(points, viewSize) {
        if (points.size >= 2 && viewSize.width > 0 && viewSize.height > 0) {
            layoutMap(points, viewSize.width, viewSize.height)
        } else {
            null
        }
    }
    val tiles by produceState<Map<TileCoord, ImageBitmap>>(emptyMap(), layout?.tileCoords) {
        val coords = layout?.tileCoords.orEmpty()
        value = if (coords.isEmpty()) emptyMap() else loadTiles(coords)
    }

    Box(
        modifier = modifier
            .background(Color(0xFFE8EEF4))
            .onSizeChanged { viewSize = it }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val map = layout ?: return@Canvas
            tiles.forEach { (tile, image) ->
                val dx = tile.x * TILE_SIZE - map.originX
                val dy = tile.y * TILE_SIZE - map.originY
                drawImage(
                    image = image,
                    dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                    dstSize = IntSize(TILE_SIZE, TILE_SIZE)
                )
            }

            val screen = map.screenPoints
            if (screen.size >= 2) {
                val route = Path().apply {
                    moveTo(screen.first().x, screen.first().y)
                    screen.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = route,
                    color = Color(0xFF2563EB).copy(alpha = 0.28f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = route,
                    color = Color(0xFF2563EB),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawEndpoint(screen.first())
                drawEndpoint(screen.last())
            }
        }
        if (layout != null) {
            Text(
                text = "© OpenStreetMap",
                color = Color(0xFF475569),
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 3.dp)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEndpoint(center: Offset) {
    drawCircle(color = Color.White, radius = 7f, center = center)
    drawCircle(color = Color(0xFF2563EB), radius = 5f, center = center)
}

private data class TileCoord(val zoom: Int, val x: Int, val y: Int)

private data class MapLayout(
    val originX: Float,
    val originY: Float,
    val tileCoords: List<TileCoord>,
    val screenPoints: List<Offset>
)

private fun parseRoutePoints(pathJson: String): List<LatLngPoint> {
    if (pathJson.isBlank() || pathJson == "[]") return emptyList()
    return try {
        val array = JSONArray(pathJson)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    LatLngPoint(
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lng")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@Suppress("DEPRECATION")
private fun geocodeEndpoints(
    context: android.content.Context,
    startAddress: String?,
    endAddress: String?
): List<LatLngPoint> {
    val geocoder = Geocoder(context, Locale.getDefault())
    val start = geocode(geocoder, startAddress) ?: return emptyList()
    val end = geocode(geocoder, endAddress) ?: return emptyList()
    return listOf(start, end)
}

@Suppress("DEPRECATION")
private fun geocode(geocoder: Geocoder, query: String?): LatLngPoint? {
    val name = query?.trim().orEmpty()
    if (name.isEmpty()) return null
    return try {
        val hit = geocoder.getFromLocationName(name, 1)?.firstOrNull() ?: return null
        LatLngPoint(latitude = hit.latitude, longitude = hit.longitude)
    } catch (_: Exception) {
        null
    }
}

private fun layoutMap(points: List<LatLngPoint>, width: Int, height: Int): MapLayout {
    val pad = 28f
    val innerW = (width - pad * 2).coerceAtLeast(1f)
    val innerH = (height - pad * 2).coerceAtLeast(1f)
    var zoom = MAX_ZOOM
    var projected = project(points, zoom)
    while (zoom > MIN_ZOOM) {
        val spanX = projected.maxOf { it.x } - projected.minOf { it.x }
        val spanY = projected.maxOf { it.y } - projected.minOf { it.y }
        if (spanX <= innerW && spanY <= innerH) break
        zoom -= 1
        projected = project(points, zoom)
    }

    val minX = projected.minOf { it.x }
    val maxX = projected.maxOf { it.x }
    val minY = projected.minOf { it.y }
    val maxY = projected.maxOf { it.y }
    val originX = minX - (width - (maxX - minX)) / 2f
    val originY = minY - (height - (maxY - minY)) / 2f
    val screen = projected.map { Offset(it.x - originX, it.y - originY) }

    val x0 = floor(originX / TILE_SIZE).toInt()
    val y0 = floor(originY / TILE_SIZE).toInt()
    val x1 = floor((originX + width - 1) / TILE_SIZE).toInt()
    val y1 = floor((originY + height - 1) / TILE_SIZE).toInt()
    val maxIndex = (1 shl zoom) - 1
    val tiles = buildList {
        for (x in x0..x1) {
            for (y in y0..y1) {
                if (x in 0..maxIndex && y in 0..maxIndex) add(TileCoord(zoom, x, y))
            }
        }
    }
    return MapLayout(originX, originY, tiles, screen)
}

private fun project(points: List<LatLngPoint>, zoom: Int): List<Offset> {
    val scale = TILE_SIZE.toDouble() * 2.0.pow(zoom)
    return points.map { point ->
        val x = (point.longitude + 180.0) / 360.0 * scale
        val sinLat = sin(point.latitude * PI / 180.0).coerceIn(-0.9999, 0.9999)
        val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * scale
        Offset(x.toFloat(), y.toFloat())
    }
}

private suspend fun loadTiles(coords: List<TileCoord>): Map<TileCoord, ImageBitmap> {
    val pending = mutableListOf<TileCoord>()
    val ready = mutableMapOf<TileCoord, ImageBitmap>()
    synchronized(tileCache) {
        coords.forEach { coord ->
            val cached = tileCache[coord.cacheKey()]
            if (cached != null) ready[coord] = cached else pending += coord
        }
    }
    if (pending.isEmpty()) return ready

    val fetched = withContext(Dispatchers.IO) {
        pending.map { coord ->
            async { coord to downloadTile(coord) }
        }.awaitAll()
    }
    fetched.forEach { (coord, bitmap) ->
        if (bitmap != null) {
            synchronized(tileCache) { tileCache[coord.cacheKey()] = bitmap }
            ready[coord] = bitmap
        }
    }
    return ready
}

private fun downloadTile(coord: TileCoord): ImageBitmap? {
    val url = "https://tile.openstreetmap.org/${coord.zoom}/${coord.x}/${coord.y}.png"
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "MileageTracker/1.0 (trip card map)")
        .build()
    return try {
        tileClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bitmap.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }
}

private fun TileCoord.cacheKey() = "$zoom/$x/$y"

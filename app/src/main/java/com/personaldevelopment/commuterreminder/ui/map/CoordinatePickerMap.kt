package com.personaldevelopment.commuterreminder.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.personaldevelopment.commuterreminder.domain.model.Station
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

private val PIN_RED = Color(0xFFC62828)
private val PIN_SIZE_DP = 36
private val MAP_CORNER = 16.dp

/**
 * Peta untuk memilih koordinat: tekan lama pada satu titik untuk memindahkan
 * pin; hasilnya dikirim lewat [onPick].
 */
@Composable
fun CoordinatePickerMap(
    station: Station,
    pickedLat: Double?,
    pickedLon: Double?,
    onPick: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnPick by rememberUpdatedState(onPick)

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(station.latitude, station.longitude))
        }
    }

    val eventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { currentOnPick(it.latitude, it.longitude) }
                return true
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(MAP_CORNER))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(MAP_CORNER)
            )
    ) {
        AndroidView(
            factory = {
                updatePickerMap(mapView, station, pickedLat, pickedLon, eventsOverlay)
                mapView
            },
            update = { view ->
                updatePickerMap(view, station, pickedLat, pickedLon, eventsOverlay)
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Text(
                text = "Tekan lama peta untuk pilih titik",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun updatePickerMap(
    mapView: MapView,
    station: Station,
    pickedLat: Double?,
    pickedLon: Double?,
    eventsOverlay: MapEventsOverlay
) {
    val overlays = mapView.overlays
    overlays.clear()
    overlays.add(eventsOverlay)

    val lat = pickedLat ?: station.latitude
    val lon = pickedLon ?: station.longitude
    val density = mapView.resources.displayMetrics.density

    val marker = Marker(mapView).apply {
        position = GeoPoint(lat, lon)
        title = station.name
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = buildPinDrawable(mapView, density)
    }
    overlays.add(marker)

    mapView.invalidate()
}

/** Pin balloon dengan lingkaran lubang di tengah, digambar presisi di bitmap. */
private fun buildPinDrawable(
    mapView: MapView,
    density: Float
): android.graphics.drawable.Drawable {
    val width = (PIN_SIZE_DP * density).toInt()
    val height = ((PIN_SIZE_DP + 10) * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val headRadius = width * 0.32f
    val headCenterX = width / 2f
    val headCenterY = headRadius + density * 2f
    val tipY = height - density * 1f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PIN_RED.toArgb()
        style = Paint.Style.FILL
    }

    // bayangan tipis
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = 0.25f).toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(headCenterX + density, headCenterY + density, headRadius, shadow)
    canvas.drawCircle(headCenterX, headCenterY, headRadius, fill)

    // badan pin (segitiga melengkung ke ujung)
    val body = Path().apply {
        moveTo(headCenterX - headRadius * 0.55f, headCenterY + headRadius * 0.75f)
        lineTo(headCenterX, tipY)
        lineTo(headCenterX + headRadius * 0.55f, headCenterY + headRadius * 0.75f)
        close()
    }
    canvas.drawPath(body, fill)

    // lingkaran lubang putih di tengah kepala
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(headCenterX, headCenterY, headRadius * 0.42f, hole)

    return android.graphics.drawable.BitmapDrawable(mapView.resources, bitmap)
}

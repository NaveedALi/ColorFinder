package com.iotek3.colorfinder.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Extracts dominant colors from a bitmap (e.g. cloth in a photo) by sampling pixels
 * and clustering into a small palette. Returns colors for display in a grid.
 */
object ColorExtractor {

    private const val MAX_SIDE = 80
    private const val DEFAULT_COLOR_COUNT = 12
    private const val MIN_BRIGHTNESS = 20f
    private const val MAX_BRIGHTNESS = 235f

    /**
     * Sample bitmap and return a list of dominant colors (e.g. for cloth).
     * @param bitmap source image
     * @param count number of colors to extract (grid size)
     * @return list of Compose Colors
     */
    fun extractColors(bitmap: Bitmap, count: Int = DEFAULT_COLOR_COUNT): List<Color> {
        val scaled = scaleToMaxSide(bitmap, MAX_SIDE)
        val pixels = samplePixels(scaled)
        if (pixels.isEmpty()) return emptyList()
        val filtered = pixels.filter { isReasonableColor(it) }
        val source = if (filtered.size >= count) filtered else pixels
        val clusters = kMeansClustering(source, count)
        return clusters
            .sortedByDescending { it.population }
            .take(count)
            .map { Color(android.graphics.Color.rgb(it.r, it.g, it.b)) }
    }

    private fun scaleToMaxSide(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSide && h <= maxSide) return bitmap
        val scale = maxSide.toFloat() / maxOf(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    private fun samplePixels(bitmap: Bitmap): List<Int> {
        val list = mutableListOf<Int>()
        val w = bitmap.width
        val h = bitmap.height
        val step = maxOf(1, minOf(w, h) / 20)
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                list.add(bitmap.getPixel(x, y))
            }
        }
        return list
    }

    private fun isReasonableColor(pixel: Int): Boolean {
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)
        val brightness = (r + g + b) / 3f
        return brightness in MIN_BRIGHTNESS..MAX_BRIGHTNESS
    }

    private data class Cluster(val r: Int, val g: Int, val b: Int, val population: Int)

    private fun kMeansClustering(pixels: List<Int>, k: Int): List<Cluster> {
        if (pixels.isEmpty() || k <= 0) return emptyList()
        val points = pixels.map { pixel ->
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            doubleArrayOf(r.toDouble(), g.toDouble(), b.toDouble())
        }
        var centroids = points.shuffled().take(k).map { it.clone() }
        repeat(15) {
            val assignments = points.map { p -> centroids.indices.minByOrNull { i -> dist(p, centroids[i]) } ?: 0 }
            centroids = (0 until k).map { i ->
                val assigned = points.filterIndexed { idx, _ -> assignments[idx] == i }
                if (assigned.isEmpty()) centroids[i]
                else doubleArrayOf(
                    assigned.map { it[0] }.average(),
                    assigned.map { it[1] }.average(),
                    assigned.map { it[2] }.average()
                )
            }
        }
        val assignments = points.map { p -> centroids.indices.minByOrNull { i -> dist(p, centroids[i]) } ?: 0 }
        return (0 until k).map { i ->
            val assigned = points.filterIndexed { idx, _ -> assignments[idx] == i }
            if (assigned.isEmpty()) Cluster(centroids[i][0].toInt(), centroids[i][1].toInt(), centroids[i][2].toInt(), 0)
            else Cluster(
                assigned.map { it[0] }.average().toInt().coerceIn(0, 255),
                assigned.map { it[1] }.average().toInt().coerceIn(0, 255),
                assigned.map { it[2] }.average().toInt().coerceIn(0, 255),
                assigned.size
            )
        }
    }

    private fun dist(a: DoubleArray, b: DoubleArray): Double =
        sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))

    /** RGB triplet for matching. */
    data class Rgb(val r: Int, val g: Int, val b: Int)

    /** Extract dominant colors as RGB triplets (for color matching). */
    fun extractColorsAsRgb(bitmap: Bitmap, count: Int = 6): List<Rgb> {
        val scaled = scaleToMaxSide(bitmap, MAX_SIDE)
        val pixels = samplePixels(scaled)
        if (pixels.isEmpty()) return emptyList()
        val filtered = pixels.filter { isReasonableColor(it) }
        val source = if (filtered.size >= count) filtered else pixels
        val clusters = kMeansClustering(source, count)
        return clusters
            .sortedByDescending { it.population }
            .take(count)
            .map { Rgb(it.r, it.g, it.b) }
    }

    /** Euclidean distance between two RGB colors (0 = same, ~441 = max). */
    fun colorDistance(a: Rgb, b: Rgb): Double = dist(
        doubleArrayOf(a.r.toDouble(), a.g.toDouble(), a.b.toDouble()),
        doubleArrayOf(b.r.toDouble(), b.g.toDouble(), b.b.toDouble())
    )

    /**
     * Match score: minimum distance from any target color to any image color.
     * Lower = better match. Consider "match" if score <= threshold (e.g. 80).
     */
    fun bestMatchScore(targetColors: List<Rgb>, imageColors: List<Rgb>): Double {
        if (targetColors.isEmpty() || imageColors.isEmpty()) return Double.MAX_VALUE
        return targetColors.minOf { tc ->
            imageColors.minOf { ic -> colorDistance(tc, ic) }
        }
    }

    /**
     * Normalized rect (0-1): [left, top, right, bottom] of the region that best matches targetColor.
     * Divides bitmap into a grid and returns the cell whose average color is closest to targetColor.
     */
    fun findMatchingRegion(bitmap: Bitmap, targetColor: Rgb, gridSize: Int = 8): FloatArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val cellW = (w / gridSize).coerceAtLeast(1)
        val cellH = (h / gridSize).coerceAtLeast(1)
        var bestDist = Double.MAX_VALUE
        var bestCol = 0
        var bestRow = 0
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val x0 = col * cellW
                val y0 = row * cellH
                val x1 = minOf(x0 + cellW, w)
                val y1 = minOf(y0 + cellH, h)
                var r = 0L
                var g = 0L
                var b = 0L
                var count = 0
                for (y in y0 until y1 step maxOf(1, (y1 - y0) / 4)) {
                    for (x in x0 until x1 step maxOf(1, (x1 - x0) / 4)) {
                        val pixel = bitmap.getPixel(x, y)
                        r += android.graphics.Color.red(pixel)
                        g += android.graphics.Color.green(pixel)
                        b += android.graphics.Color.blue(pixel)
                        count++
                    }
                }
                if (count == 0) continue
                val avg = Rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
                val d = colorDistance(targetColor, avg)
                if (d < bestDist) {
                    bestDist = d
                    bestCol = col
                    bestRow = row
                }
            }
        }
        return floatArrayOf(
            bestCol.toFloat() / gridSize,
            bestRow.toFloat() / gridSize,
            (bestCol + 1).toFloat() / gridSize,
            (bestRow + 1).toFloat() / gridSize
        )
    }
}

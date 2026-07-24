package io.github.chrislo27.dotmatrix

import com.sun.net.httpserver.HttpServer
import io.github.chrislo27.dotmatrix.img.Color
import java.net.InetSocketAddress
import javax.imageio.ImageIO

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)

    server.createContext("/api/sign") { exchange ->
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")

        val query = exchange.requestURI.query ?: ""
        val params = query.split("&").associate {
            val parts = it.split("=")
            val key = parts.getOrElse(0) { "" }
            val value = java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            key to value
        }

        val width = params["width"] ?: "160"
        val height = params["height"] ?: "16"

        val routeText = parseDestSignEscapes(params["route"] ?: "")
        val routeFont = params["routeFont"] ?: "16d"

        val line1Raw = parseDestSignEscapes(params["line1"] ?: "NOT IN SERVICE")
        val line1Spacing = params["line1Spacing"] ?: "0"
        val line1Text = applyLetterSpacing(line1Raw, line1Spacing)

        val isStacked = params.containsKey("line2") && params["line2"]!!.isNotEmpty()
        val line1Font = params["line1Font"] ?: if (isStacked) "8d" else "15d"

        val line2Raw = parseDestSignEscapes(params["line2"] ?: "")
        val line2Spacing = params["line2Spacing"] ?: "0"
        val line2Text = applyLetterSpacing(line2Raw, line2Spacing)
        val line2Font = params["line2Font"] ?: "16d"

        val hexColor = params["color"] ?: "FF9000"
        val signColor = try {
            val r = hexColor.substring(0, 2).toInt(16)
            val g = hexColor.substring(2, 4).toInt(16)
            val b = hexColor.substring(4, 6).toInt(16)
            Color(r, g, b, 255)
        } catch (e: Exception) {
            DestSign.ORANGE
        }

        try {
            val fonts = DestSignTest.fonts

            val routeLines = if (routeText.isNotEmpty()) {
                val run = GlyphRun(fonts.getValue(routeFont), routeText, signColor)
                val layout = GlyphLayout(listOf(run), VerticalAlignment.CENTRE, TextAlignment.CENTRE)
                LayoutLines(listOf(layout), LineSpacing.FLUSH_TO_EDGES)
            } else {
                LayoutLines(emptyList())
            }

            val destLayouts = mutableListOf<GlyphLayout>()

            if (!isStacked) {
                val run1 = GlyphRun(fonts.getValue(line1Font), line1Text, signColor)
                destLayouts.add(GlyphLayout(listOf(run1), VerticalAlignment.CENTRE, TextAlignment.CENTRE))
            } else {
                val run1 = GlyphRun(fonts.getValue(line1Font), line1Text, signColor)
                val run2 = GlyphRun(fonts.getValue(line2Font), line2Text, signColor)
                destLayouts.add(GlyphLayout(listOf(run1), VerticalAlignment.TOP, TextAlignment.CENTRE))
                destLayouts.add(GlyphLayout(listOf(run2), VerticalAlignment.BOTTOM, TextAlignment.CENTRE))
            }

            val destLines = LayoutLines(destLayouts, LineSpacing.FLUSH_TO_EDGES)
            val frame = DestinationFrame(listOf(destLines))
            val destination = Destination(route = routeLines, frames = listOf(frame), routeAlignment = TextAlignment.LEFT)

            val sign = DestSign(width.toInt(), height.toInt())
            sign.destination = destination
            val matrixImage = sign.generateImageForState(0)

            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, 0)
            ImageIO.write(matrixImage.backing, "png", exchange.responseBody)
            exchange.responseBody.close()

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "API Error: Check if your font names are correct."
            exchange.sendResponseHeaders(500, errorMsg.length.toLong())
            exchange.responseBody.write(errorMsg.toByteArray())
            exchange.responseBody.close()
        }
    }

    server.start()
    println("signmatrix API running on http://localhost:8080/api/sign")
}

fun applyLetterSpacing(parsedText: String, spacingStr: String): String {
    val spaces = spacingStr.toIntOrNull() ?: 0
    if (spaces <= 0 || parsedText.isEmpty()) return parsedText

    val hairSpaces = "\u200A".repeat(spaces)

    return parsedText.split(" ").joinToString(" ") { word ->
        word.toList().joinToString(hairSpaces)
    }
}

fun parseDestSignEscapes(str: String): String {
    val strb = StringBuilder()
    var inEscape = false
    var inGlyph = false
    for (c in str) {
        if (inGlyph) {
            val hex: Int = (c.toString().toInt(16))
            strb.append('\uE000' + hex)
            inEscape = false
            inGlyph = false
        } else if (inEscape) {
            when (c) {
                's' -> strb.append(' ')
                '\\' -> strb.append('\\')
                '1' -> strb.append('\u200A')
                '~' -> strb.append('\u0015')
                '!' -> strb.append('!')
                'g' -> inGlyph = true
                else -> {}
            }
            inEscape = false
        } else {
            if (c == '\\') inEscape = true else strb.append(c)
        }
    }
    if (inEscape) strb.append('\\')
    return strb.toString()
}
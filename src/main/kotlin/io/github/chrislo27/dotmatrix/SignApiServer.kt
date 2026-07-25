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

        val frameDelay = params["speed"]?.toIntOrNull() ?: 2500

        val hexColor = params["color"] ?: "FF9000"
        val signColor = try {
            val r = hexColor.substring(0, 2).toInt(16)
            val g = hexColor.substring(2, 4).toInt(16)
            val b = hexColor.substring(4, 6).toInt(16)
            Color(r, g, b, 255)
        } catch (_: Exception) {
            DestSign.ORANGE
        }

        try {
            val fonts = DestSignTest.fonts

            val routeFrames = (params["route"] ?: "").split("|")
            val routeFontFrames = (params["routeFont"] ?: "16d").split("|")

            val line1Frames = (params["line1"] ?: "NOT IN SERVICE").split("|")
            val line1FontFrames = (params["line1Font"] ?: "").split("|")
            val line1SpacingFrames = (params["line1Spacing"] ?: "0").split("|")

            val line2Frames = (params["line2"] ?: "").split("|")
            val line2FontFrames = (params["line2Font"] ?: "").split("|")
            val line2SpacingFrames = (params["line2Spacing"] ?: "0").split("|")

            val animFrames = (params["animation"] ?: "").split("|")
            val animSpeedFrames = (params["animSpeed"] ?: "").split("|")

            val globalRouteStr = routeFrames.firstOrNull() ?: ""
            val globalRouteFont = routeFontFrames.firstOrNull() ?: "16d"

            val routeSuffix = params["routeSuffix"] ?: ""
            val routeSuffixFont = params["routeSuffixFont"] ?: "8d"

            val routeText = parseDestSignEscapes(globalRouteStr)
            val routeSuffixText = parseDestSignEscapes(routeSuffix)

            val routeLines = if (routeText.isNotEmpty() || routeSuffixText.isNotEmpty()) {
                val runs = mutableListOf<GlyphRun>()
                if (routeText.isNotEmpty()) {
                    runs.add(GlyphRun(fonts.getValue(globalRouteFont), routeText, signColor))
                }
                if (routeSuffixText.isNotEmpty()) {
                    runs.add(GlyphRun(fonts.getValue(routeSuffixFont), routeSuffixText, signColor))
                }
                val layout = GlyphLayout(runs, VerticalAlignment.BOTTOM, TextAlignment.CENTRE)
                LayoutLines(listOf(layout), LineSpacing.FLUSH_TO_EDGES)
            } else {
                LayoutLines(emptyList())
            }
            val routeAlignStr = params["routeAlign"]?.uppercase() ?: "LEFT"
            val routeAlign = if (routeAlignStr == "RIGHT") TextAlignment.RIGHT else TextAlignment.LEFT

            val l1AlignFrames = (params["line1Align"] ?: "").split("|")
            val l2AlignFrames = (params["line2Align"] ?: "").split("|")
            val verticalSpacingFrames = (params["verticalSpacing"] ?: "").split("|")

            val frameCount = maxOf(line1Frames.size, line2Frames.size)

            val destFrames = mutableListOf<DestinationFrame>()
            val screenTimes = mutableListOf<Float>()

            for (i in 0 until frameCount) {
                val l1Str = line1Frames.getOrElse(i) { line1Frames.lastOrNull() ?: "" }
                val l1FontStr = line1FontFrames.getOrElse(i) { line1FontFrames.lastOrNull() ?: "" }
                val l1Space = line1SpacingFrames.getOrElse(i) { line1SpacingFrames.lastOrNull() ?: "0" }

                val l2Str = line2Frames.getOrElse(i) { line2Frames.lastOrNull() ?: "" }
                val l2FontStr = line2FontFrames.getOrElse(i) { line2FontFrames.lastOrNull() ?: "" }
                val l2Space = line2SpacingFrames.getOrElse(i) { line2SpacingFrames.lastOrNull() ?: "0" }

                val line1Raw = parseDestSignEscapes(l1Str)
                val line1Text = applyLetterSpacing(line1Raw, l1Space)

                val line2Raw = parseDestSignEscapes(l2Str)
                val line2Text = applyLetterSpacing(line2Raw, l2Space)

                val isStacked = l2Str.isNotEmpty()
                val line1Font = if (l1FontStr.isNotBlank()) l1FontStr else (if (isStacked) "8d" else "15d")
                val line2Font = if (l2FontStr.isNotBlank()) l2FontStr else "16d"

                val destLayouts = mutableListOf<GlyphLayout>()

                val animStr = animFrames.getOrElse(i) { animFrames.lastOrNull() ?: "NONE" }
                val animSpeedStr = animSpeedFrames.getOrElse(i) { animSpeedFrames.lastOrNull() ?: "0.25" }
                val animSpeed = animSpeedStr.toFloatOrNull() ?: 0.25f

                val animationType = when (animStr.uppercase()) {
                    "FALLDOWN" -> AnimationType.Falldown(animSpeed)
                    "FALLUP" -> AnimationType.Fallup(animSpeed)
                    "SIDEWIPE" -> AnimationType.Sidewipe(animSpeed)
                    "SCROLL" -> AnimationType.HorizontalScroll(animSpeed)
                    else -> AnimationType.NoAnimation
                }

                val l1AlignStr = l1AlignFrames.getOrElse(i) { l1AlignFrames.lastOrNull() ?: "CENTRE" }
                val l2AlignStr = l2AlignFrames.getOrElse(i) { l2AlignFrames.lastOrNull() ?: "CENTRE" }
                val vSpacingStr = verticalSpacingFrames.getOrElse(i) { verticalSpacingFrames.lastOrNull() ?: "FLUSH" }

                val l1Align = when(l1AlignStr.uppercase()) {
                    "LEFT" -> TextAlignment.LEFT
                    "RIGHT" -> TextAlignment.RIGHT
                    else -> TextAlignment.CENTRE
                }
                val l2Align = when(l2AlignStr.uppercase()) {
                    "LEFT" -> TextAlignment.LEFT
                    "RIGHT" -> TextAlignment.RIGHT
                    else -> TextAlignment.CENTRE
                }
                val vSpacing = if (vSpacingStr.uppercase() == "EQUISPACED") LineSpacing.EQUISPACED else LineSpacing.FLUSH_TO_EDGES

                if (!isStacked) {
                    val run1 = GlyphRun(fonts.getValue(line1Font), line1Text, signColor)
                    destLayouts.add(GlyphLayout(listOf(run1), VerticalAlignment.CENTRE, l1Align))
                } else {
                    val run1 = GlyphRun(fonts.getValue(line1Font), line1Text, signColor)
                    val run2 = GlyphRun(fonts.getValue(line2Font), line2Text, signColor)
                    destLayouts.add(GlyphLayout(listOf(run1), VerticalAlignment.TOP, l1Align))
                    destLayouts.add(GlyphLayout(listOf(run2), VerticalAlignment.BOTTOM, l2Align))
                }

                val destLines = LayoutLines(destLayouts, vSpacing)

                destFrames.add(DestinationFrame(
                    layoutLines = listOf(destLines),
                    animation = animationType
                ))
                screenTimes.add(frameDelay / 1000f)
            }

            val destination = Destination(
                route = routeLines,
                frames = destFrames,
                screenTimes = screenTimes,
                routeAlignment = routeAlign
            )

            val sign = DestSign(width.toInt(), height.toInt(), defaultAnimation = AnimationType.Falldown(0.25f))
            sign.destination = destination

            if (sign.isAnimated()) {
                exchange.responseHeaders.add("Content-Type", "image/gif")
                exchange.sendResponseHeaders(200, 0)
                sign.generateGif(exchange.responseBody)
            } else {
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, 0)
                ImageIO.write(sign.generateImageForState(0).backing, "png", exchange.responseBody)
            }
            exchange.responseBody.close()

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "err: Check if your font names are correct."
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
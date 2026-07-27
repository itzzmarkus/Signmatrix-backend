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

        val query = exchange.requestURI.rawQuery ?: ""
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
        val offColorHex = params["offColor"] ?: "222222"
        val unlitColor = try {
            val r = offColorHex.substring(0, 2).toInt(16)
            val g = offColorHex.substring(2, 4).toInt(16)
            val b = offColorHex.substring(4, 6).toInt(16)
            Color(r, g, b, 255)
        } catch (_: Exception) {
            Color(64, 64, 64, 255)
        }

        val ledShape = params["ledShape"] ?: "square"
        val ledSize = params["ledSize"]?.toIntOrNull() ?: 3
        val ledGap = params["ledGap"]?.toIntOrNull() ?: 1

        val isCircles = ledShape.uppercase() == "ROUND" && ledSize >= 4

        try {
            val fonts = DestSignTest.fonts

            val routeFrames = (params["route"] ?: "").split("|")
            val routeFontFrames = (params["routeFont"] ?: "16d").split("|")

            val line1Frames = (params["line1"] ?: "NOT IN SERVICE").split("|")
            val line1FontFrames = (params["line1Font"] ?: "").split("|")
            val line1ColorFrames = (params["line1Color"] ?: "").split("|")
            val line1SpacingFrames = (params["line1Spacing"] ?: "0").split("|")

            val line2Frames = (params["line2"] ?: "").split("|")
            val line2FontFrames = (params["line2Font"] ?: "").split("|")
            val line2ColorFrames = (params["line2Color"] ?: "").split("|")
            val line2SpacingFrames = (params["line2Spacing"] ?: "0").split("|")

            val animFrames = (params["animation"] ?: "").split("|")
            val animSpeedFrames = (params["animSpeed"] ?: "").split("|")
            val delayFrames = (params["delay"] ?: "").split("|")

            val globalRouteStr = routeFrames.firstOrNull() ?: ""
            val globalRouteFont = routeFontFrames.firstOrNull() ?: "16d"

            val routeSuffix = params["routeSuffix"] ?: ""
            val routeSuffixFont = params["routeSuffixFont"] ?: "8d"

            val routeText = parseDestSignEscapes(globalRouteStr)
            val routeSuffixText = parseDestSignEscapes(routeSuffix)

            val routeColor = parseCustomColor(params["routeColor"], signColor)
            val routeSuffixColor = parseCustomColor(params["routeSuffixColor"], signColor)

            val routeLines = if (routeText.isNotEmpty() || routeSuffixText.isNotEmpty()) {
                val runs = mutableListOf<GlyphRun>()
                if (routeText.isNotEmpty()) {
                    runs.add(GlyphRun(fonts.getValue(globalRouteFont), routeText, routeColor))
                }
                if (routeSuffixText.isNotEmpty()) {
                    runs.add(GlyphRun(fonts.getValue(routeSuffixFont), routeSuffixText, routeSuffixColor))
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
            val destination = buildDestination(
                frameCount, fonts, signColor, routeLines, routeAlign,
                line1Frames, line1FontFrames, line1ColorFrames, line1SpacingFrames,
                line2Frames, line2FontFrames, line2ColorFrames, line2SpacingFrames,
                animFrames, animSpeedFrames, l1AlignFrames, l2AlignFrames, verticalSpacingFrames, delayFrames, frameDelay
            )

            val sign = DestSign(
                width.toInt(),
                height.toInt(),
                ledSize = ledSize,
                ledSpacing = ledGap,
                circles = isCircles,
                offColor = unlitColor,
                defaultAnimation = AnimationType.Falldown(0.25f)
            )

            sign.destination = destination

            val prLine1Frames = (params["prLine1"] ?: "").split("|")
            val prLine2Frames = (params["prLine2"] ?: "").split("|")
            val prDelayFrames = (params["prDelay"] ?: "").split("|")

            val prLine1ColorFrames = (params["prLine1Color"] ?: "").split("|")
            val prLine2ColorFrames = (params["prLine2Color"] ?: "").split("|")

            if (prLine1Frames.any { it.isNotBlank() } || prLine2Frames.any { it.isNotBlank() }) {
                val prCount = maxOf(prLine1Frames.size, prLine2Frames.size)

                sign.pr = buildDestination(
                    prCount, fonts, signColor,
                    LayoutLines(emptyList()), TextAlignment.LEFT,
                    prLine1Frames,
                    (params["prLine1Font"] ?: "").split("|"),
                    prLine1ColorFrames,
                    (params["prLine1Spacing"] ?: "0").split("|"),
                    prLine2Frames,
                    (params["prLine2Font"] ?: "").split("|"),
                    prLine2ColorFrames,
                    (params["prLine2Spacing"] ?: "0").split("|"),
                    (params["prAnimation"] ?: "").split("|"),
                    (params["prAnimSpeed"] ?: "0.25").split("|"),
                    (params["prLine1Align"] ?: "").split("|"),
                    (params["prLine2Align"] ?: "").split("|"),
                    (params["prVerticalSpacing"] ?: "").split("|"),
                    prDelayFrames, frameDelay
                )
            }

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
            println("SIGN_GENERATED: $query")

        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "err: Check if your font names are correct."
            exchange.sendResponseHeaders(500, errorMsg.length.toLong())
            exchange.responseBody.write(errorMsg.toByteArray())
            exchange.responseBody.close()
        }
    }

    server.createContext("/api/ping") { exchange ->
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        val response = "pong!"
        exchange.sendResponseHeaders(200, response.length.toLong())
        exchange.responseBody.write(response.toByteArray())
        exchange.responseBody.close()
        println("ping")
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

fun buildDestination(
    frameCount: Int,
    fonts: Map<String, DotMtxFont>,
    signColor: Color,
    routeLines: LayoutLines,
    routeAlign: TextAlignment,
    line1Frames: List<String>,
    line1FontFrames: List<String>,
    line1ColorFrames: List<String>,
    line1SpacingFrames: List<String>,
    line2Frames: List<String>,
    line2FontFrames: List<String>,
    line2ColorFrames: List<String>,
    line2SpacingFrames: List<String>,
    animFrames: List<String>,
    animSpeedFrames: List<String>,
    l1AlignFrames: List<String>,
    l2AlignFrames: List<String>,
    verticalSpacingFrames: List<String>,
    delayFrames: List<String>,
    frameDelay: Int
): Destination {
    val destFrames = mutableListOf<DestinationFrame>()
    val screenTimes = mutableListOf<Float>()

    for (i in 0 until frameCount) {
        val l1Str = line1Frames.getOrElse(i) { line1Frames.lastOrNull() ?: "" }
        val l1FontStr = line1FontFrames.getOrElse(i) { line1FontFrames.lastOrNull() ?: "" }
        val l1ColorStr = line1ColorFrames.getOrElse(i) { line1ColorFrames.lastOrNull() ?: "" }
        val l1Space = line1SpacingFrames.getOrElse(i) { line1SpacingFrames.lastOrNull() ?: "0" }

        val l2Str = line2Frames.getOrElse(i) { line2Frames.lastOrNull() ?: "" }
        val l2FontStr = line2FontFrames.getOrElse(i) { line2FontFrames.lastOrNull() ?: "" }
        val l2ColorStr = line2ColorFrames.getOrElse(i) { line2ColorFrames.lastOrNull() ?: "" }
        val l2Space = line2SpacingFrames.getOrElse(i) { line2SpacingFrames.lastOrNull() ?: "0" }

        val isStacked = l2Str.isNotEmpty()
        val defaultL1Font = if (isStacked) "8d" else "15d"
        val defaultL2Font = "16d"

        val l1Runs = buildGlyphRuns(l1Str, l1FontStr, l1ColorStr, l1Space, defaultL1Font, signColor, fonts)
        val l2Runs = buildGlyphRuns(l2Str, l2FontStr, l2ColorStr, l2Space, defaultL2Font, signColor, fonts)

        val animStr = animFrames.getOrElse(i) { animFrames.lastOrNull() ?: "NONE" }
        val animSpeed = animSpeedFrames.getOrElse(i) { animSpeedFrames.lastOrNull() ?: "0.25" }.toFloatOrNull() ?: 0.25f
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

        val l1Align =
            if (l1AlignStr.uppercase() == "LEFT") TextAlignment.LEFT else if (l1AlignStr.uppercase() == "RIGHT") TextAlignment.RIGHT else TextAlignment.CENTRE
        val l2Align =
            if (l2AlignStr.uppercase() == "LEFT") TextAlignment.LEFT else if (l2AlignStr.uppercase() == "RIGHT") TextAlignment.RIGHT else TextAlignment.CENTRE
        val vSpacing =
            if (vSpacingStr.uppercase() == "EQUISPACED") LineSpacing.EQUISPACED else LineSpacing.FLUSH_TO_EDGES

        val destLayouts = mutableListOf<GlyphLayout>()
        if (!isStacked) {
            destLayouts.add(GlyphLayout(l1Runs, VerticalAlignment.CENTRE, l1Align))
        } else {
            destLayouts.add(GlyphLayout(l1Runs, VerticalAlignment.TOP, l1Align))
            destLayouts.add(GlyphLayout(l2Runs, VerticalAlignment.BOTTOM, l2Align))
        }

        destFrames.add(
            DestinationFrame(
                layoutLines = listOf(LayoutLines(destLayouts, vSpacing)),
                animation = animationType
            )
        )

        val delayStr = delayFrames.getOrElse(i) { delayFrames.lastOrNull() ?: "" }
        val thisDelay = delayStr.toIntOrNull() ?: frameDelay

        screenTimes.add(thisDelay / 1000f)
    }

    return Destination(routeLines, destFrames, screenTimes, routeAlign)
}

fun buildGlyphRuns(
    rawString: String,
    fontString: String,
    colorString: String,
    spacingStr: String,
    defaultFontFallback: String,
    defaultColor: Color,
    fonts: Map<String, DotMtxFont>
): List<GlyphRun> {
    if (rawString.isEmpty()) return emptyList()

    val textSegments = rawString.split("^")
    val fontSegments = fontString.split("^")
    val colorSegments = colorString.split("^")

    return textSegments.mapIndexed { index, textSeg ->
        val fontName = fontSegments.getOrElse(index) { fontSegments.lastOrNull() ?: defaultFontFallback }.ifBlank { defaultFontFallback }
        val hexColor = colorSegments.getOrElse(index) { colorSegments.lastOrNull() ?: "" }.ifBlank { "" }

        val parsedText = parseDestSignEscapes(textSeg)
        val spacedText = applyLetterSpacing(parsedText, spacingStr)
        val resolvedFont = fonts[fontName] ?: fonts[defaultFontFallback] ?: fonts.values.first()

        val segmentColor = if (hexColor.length == 6) {
            try {
                Color(
                    hexColor.substring(0, 2).toInt(16),
                    hexColor.substring(2, 4).toInt(16),
                    hexColor.substring(4, 6).toInt(16),
                    255
                )
            } catch (_: Exception) {
                defaultColor
            }
        } else {
            defaultColor
        }

        GlyphRun(resolvedFont, spacedText, segmentColor)
    }
}

fun parseCustomColor(hexColor: String?, defaultColor: Color): Color {
    val cleanHex = hexColor?.replace("#", "") ?: ""
    if (cleanHex.length != 6) return defaultColor
    return try {
        Color(
            cleanHex.substring(0, 2).toInt(16),
            cleanHex.substring(2, 4).toInt(16),
            cleanHex.substring(4, 6).toInt(16),
            255
        )
    } catch (_: Exception) {
        defaultColor
    }
}
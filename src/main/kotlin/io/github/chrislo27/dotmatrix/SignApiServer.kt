package io.github.chrislo27.dotmatrix

import com.sun.net.httpserver.HttpServer
import io.github.chrislo27.dotmatrix.img.Color
import java.net.InetSocketAddress
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.IIOImage
import java.awt.image.BufferedImage
import java.io.OutputStream

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
        } catch (e: Exception) {
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

            val frameCount = maxOf(line1Frames.size, line2Frames.size, routeFrames.size)

            val bufferedImages = mutableListOf<BufferedImage>()

            for (i in 0 until frameCount) {
                val rStr = routeFrames.getOrElse(i) { routeFrames.lastOrNull() ?: "" }
                val rFont = routeFontFrames.getOrElse(i) { routeFontFrames.lastOrNull() ?: "16d" }

                val l1Str = line1Frames.getOrElse(i) { line1Frames.lastOrNull() ?: "" }
                val l1FontStr = line1FontFrames.getOrElse(i) { line1FontFrames.lastOrNull() ?: "" }
                val l1Space = line1SpacingFrames.getOrElse(i) { line1SpacingFrames.lastOrNull() ?: "0" }

                val l2Str = line2Frames.getOrElse(i) { line2Frames.lastOrNull() ?: "" }
                val l2FontStr = line2FontFrames.getOrElse(i) { line2FontFrames.lastOrNull() ?: "" }
                val l2Space = line2SpacingFrames.getOrElse(i) { line2SpacingFrames.lastOrNull() ?: "0" }
                val routeText = parseDestSignEscapes(rStr)

                val line1Raw = parseDestSignEscapes(l1Str)
                val line1Text = applyLetterSpacing(line1Raw, l1Space)

                val line2Raw = parseDestSignEscapes(l2Str)
                val line2Text = applyLetterSpacing(line2Raw, l2Space)

                val isStacked = l2Str.isNotEmpty()
                val line1Font = if (l1FontStr.isNotBlank()) l1FontStr else (if (isStacked) "8d" else "15d")
                val line2Font = if (l2FontStr.isNotBlank()) l2FontStr else "16d"

                val routeLines = if (routeText.isNotEmpty()) {
                    val run = GlyphRun(fonts.getValue(rFont), routeText, signColor)
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
                bufferedImages.add(sign.generateImageForState(0).backing)
            }

            if (bufferedImages.size > 1) {
                exchange.responseHeaders.add("Content-Type", "image/gif")
                exchange.sendResponseHeaders(200, 0)
                writeAnimatedGif(bufferedImages, frameDelay, exchange.responseBody)
            } else {
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, 0)
                ImageIO.write(bufferedImages[0], "png", exchange.responseBody)
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

fun writeAnimatedGif(frames: List<BufferedImage>, delayMs: Int, out: OutputStream) {
    val writer = ImageIO.getImageWritersByFormatName("gif").next()
    val ios = ImageIO.createImageOutputStream(out)
    writer.output = ios
    writer.prepareWriteSequence(null)

    for (frame in frames) {
        val imageMetaData = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), null)
        val metaFormatName = imageMetaData.nativeMetadataFormatName
        val root = imageMetaData.getAsTree(metaFormatName) as IIOMetadataNode

        val graphicsControlExtensionNode = IIOMetadataNode("GraphicControlExtension")
        graphicsControlExtensionNode.setAttribute("disposalMethod", "none")
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE")
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE")
        graphicsControlExtensionNode.setAttribute("delayTime", (delayMs / 10).toString())
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0")

        val appExtensionsNode = IIOMetadataNode("ApplicationExtensions")
        val appExtensionNode = IIOMetadataNode("ApplicationExtension")
        appExtensionNode.setAttribute("applicationID", "NETSCAPE")
        appExtensionNode.setAttribute("authenticationCode", "2.0")
        appExtensionNode.userObject = byteArrayOf(1, 0, 0)
        appExtensionsNode.appendChild(appExtensionNode)

        root.appendChild(graphicsControlExtensionNode)
        root.appendChild(appExtensionsNode)

        imageMetaData.setFromTree(metaFormatName, root)
        writer.writeToSequence(IIOImage(frame, null, imageMetaData), null)
    }
    writer.endWriteSequence()
    ios.close()
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
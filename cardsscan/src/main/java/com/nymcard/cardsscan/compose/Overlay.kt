package com.nymcard.cardsscan.compose

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

fun DrawScope.drawCardOverlay() {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // Calculate card frame dimensions (maintaining 480:302 aspect ratio)
    val cardMargin = 32.dp.toPx()
    val cardWidth = canvasWidth - (cardMargin * 2)
    val cardHeight = cardWidth * (302f / 480f)

    val cardLeft = (canvasWidth - cardWidth) / 2
    val cardTop = (canvasHeight - cardHeight) / 2
    val cardRight = cardLeft + cardWidth
    val cardBottom = cardTop + cardHeight

    val cornerRadius = 12.dp.toPx()

    // First, draw the full semi-transparent background
    drawRect(
        color = Color(0x992F3542), // Traditional camera_background color
        size = Size(canvasWidth, canvasHeight)
    )

    // Then, clear the card area (make it completely transparent like traditional UI)
    drawRoundRect(
        color = Color.Transparent,
        topLeft = androidx.compose.ui.geometry.Offset(cardLeft, cardTop),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(cornerRadius),
        blendMode = BlendMode.Clear // This clears the area, making it fully transparent
    )

    // Draw corner indicators like traditional widget.Overlay
    drawCornerIndicators(
        cardLeft = cardLeft,
        cardTop = cardTop,
        cardRight = cardRight,
        cardBottom = cardBottom,
        cornerRadius = cornerRadius
    )
}

private fun DrawScope.drawCornerIndicators(
    cardLeft: Float,
    cardTop: Float,
    cardRight: Float,
    cardBottom: Float,
    cornerRadius: Float
) {
    val cornerColor = Color(0xFF4CD964) // Traditional corner_color (bright green)
    val strokeWidth = 6.dp.toPx()
    val lineLength = 20.dp.toPx()
    val offset = 1.dp.toPx()

    // Top Left Corner
    drawTopLeftCorner(
        centerX = cardLeft - offset + cornerRadius,
        centerY = cardTop - offset + cornerRadius,
        radius = cornerRadius,
        color = cornerColor,
        strokeWidth = strokeWidth,
        lineLength = lineLength
    )

    // Top Right Corner
    drawTopRightCorner(
        centerX = cardRight + offset - cornerRadius,
        centerY = cardTop - offset + cornerRadius,
        radius = cornerRadius,
        color = cornerColor,
        strokeWidth = strokeWidth,
        lineLength = lineLength
    )

    // Bottom Right Corner
    drawBottomRightCorner(
        centerX = cardRight + offset - cornerRadius,
        centerY = cardBottom + offset - cornerRadius,
        radius = cornerRadius,
        color = cornerColor,
        strokeWidth = strokeWidth,
        lineLength = lineLength
    )

    // Bottom Left Corner
    drawBottomLeftCorner(
        centerX = cardLeft - offset + cornerRadius,
        centerY = cardBottom + offset - cornerRadius,
        radius = cornerRadius,
        color = cornerColor,
        strokeWidth = strokeWidth,
        lineLength = lineLength
    )
}

private fun DrawScope.drawTopLeftCorner(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    lineLength: Float
) {
    // Draw arc from 180° to 270° (top-left quarter circle)
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
        size = Size(radius * 2, radius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )

    // Vertical line extending down
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX - radius, centerY),
        end = androidx.compose.ui.geometry.Offset(centerX - radius, centerY + lineLength),
        strokeWidth = strokeWidth
    )

    // Horizontal line extending right
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX, centerY - radius),
        end = androidx.compose.ui.geometry.Offset(centerX + lineLength, centerY - radius),
        strokeWidth = strokeWidth
    )
}

private fun DrawScope.drawTopRightCorner(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    lineLength: Float
) {
    // Draw arc from 270° to 360° (top-right quarter circle)
    drawArc(
        color = color,
        startAngle = 270f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
        size = Size(radius * 2, radius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )

    // Vertical line extending down
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX + radius, centerY),
        end = androidx.compose.ui.geometry.Offset(centerX + radius, centerY + lineLength),
        strokeWidth = strokeWidth
    )

    // Horizontal line extending left
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX, centerY - radius),
        end = androidx.compose.ui.geometry.Offset(centerX - lineLength, centerY - radius),
        strokeWidth = strokeWidth
    )
}

private fun DrawScope.drawBottomRightCorner(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    lineLength: Float
) {
    // Draw arc from 0° to 90° (bottom-right quarter circle)
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
        size = Size(radius * 2, radius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )

    // Vertical line extending up
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX + radius, centerY),
        end = androidx.compose.ui.geometry.Offset(centerX + radius, centerY - lineLength),
        strokeWidth = strokeWidth
    )

    // Horizontal line extending left
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX, centerY + radius),
        end = androidx.compose.ui.geometry.Offset(centerX - lineLength, centerY + radius),
        strokeWidth = strokeWidth
    )
}

private fun DrawScope.drawBottomLeftCorner(
    centerX: Float,
    centerY: Float,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    lineLength: Float
) {
    // Draw arc from 90° to 180° (bottom-left quarter circle)
    drawArc(
        color = color,
        startAngle = 90f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - radius),
        size = Size(radius * 2, radius * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
    )

    // Vertical line extending up
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX - radius, centerY),
        end = androidx.compose.ui.geometry.Offset(centerX - radius, centerY - lineLength),
        strokeWidth = strokeWidth
    )

    // Horizontal line extending right
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(centerX, centerY + radius),
        end = androidx.compose.ui.geometry.Offset(centerX + lineLength, centerY + radius),
        strokeWidth = strokeWidth
    )
}
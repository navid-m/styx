package com.styx.ui

import java.awt.*
import javax.swing.border.AbstractBorder

class RoundedBorder(private val color: Color, private val thickness: Int, private val radius: Int) : AbstractBorder() {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.stroke = BasicStroke(thickness.toFloat())
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius)
        g2.dispose()
    }

    override fun getBorderInsets(c: Component): Insets {
        return Insets(thickness, thickness, thickness, thickness)
    }

    override fun isBorderOpaque(): Boolean = false
}
package com.styx.ui

import java.awt.*

/**
 * A modified version of FlowLayout that allows components to wrap to the next line.
 *
 * Unlike FlowLayout, this layout calculates preferred size for wrapping components.
 */
class WrapLayout(align: Int = LEFT, hgap: Int = 5, vgap: Int = 5) : FlowLayout(align, hgap, vgap) {
    override fun preferredLayoutSize(target: Container): Dimension {
        return layoutSize(target, true)
    }

    override fun minimumLayoutSize(target: Container): Dimension {
        val minimum = layoutSize(target, false)
        minimum.width -= hgap + hgap
        return minimum
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = if (target.size.width == 0) Integer.MAX_VALUE else target.size.width
            val hgap = hgap
            val vgap = vgap
            val insets: Insets = target.insets
            val horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2)
            val maxWidth = targetWidth - horizontalInsetsAndGap

            val dim = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0

            val nmembers = target.componentCount

            for (i in 0 until nmembers) {
                val m: Component = target.getComponent(i)

                if (m.isVisible) {
                    val d = if (preferred) m.preferredSize else m.minimumSize

                    if (rowWidth + d.width > maxWidth) {
                        addRow(dim, rowWidth, rowHeight)
                        rowWidth = 0
                        rowHeight = 0
                    }

                    if (rowWidth != 0) {
                        rowWidth += hgap
                    }

                    rowWidth += d.width
                    rowHeight = maxOf(rowHeight, d.height)
                }
            }

            addRow(dim, rowWidth, rowHeight)

            dim.width += horizontalInsetsAndGap
            dim.height += insets.top + insets.bottom + vgap * 2

            val scrollableTracksViewportWidth = target.parent
            if (scrollableTracksViewportWidth != null && scrollableTracksViewportWidth.size.width > 0) {
                dim.width = scrollableTracksViewportWidth.size.width
            }

            return dim
        }
    }

    private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
        dim.width = maxOf(dim.width, rowWidth)

        if (dim.height > 0) {
            dim.height += vgap
        }

        dim.height += rowHeight
    }
}

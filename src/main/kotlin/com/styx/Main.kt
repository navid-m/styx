package com.styx

import javax.swing.*
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.styx.ui.GameLauncher

fun main() {
    FlatMacDarkLaf.setup()
    SwingUtilities.invokeLater {
        val launcher = GameLauncher()
        launcher.isVisible = true
    }
}

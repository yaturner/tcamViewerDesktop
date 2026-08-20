package com.das.tcamviewerdesktop.factory

import com.das.tcamviewerdesktop.palette.Arctic
import com.das.tcamviewerdesktop.palette.Banded
import com.das.tcamviewerdesktop.palette.Blackhot
import com.das.tcamviewerdesktop.palette.DoubleRainbow
import com.das.tcamviewerdesktop.palette.Fusion
import com.das.tcamviewerdesktop.palette.Gray
import com.das.tcamviewerdesktop.palette.Ironblack
import com.das.tcamviewerdesktop.palette.Isotherm
import com.das.tcamviewerdesktop.palette.Rainbow
import com.das.tcamviewerdesktop.palette.Sepia

class PaletteFactory {
    val paletteNames: Array<String?> =
        arrayOf<String?>(
            "Arctic",
            "Banded",
            "Blackhot",
            "DoubleRainbow",
            "Fusion",
            "Gray",
            "Ironblack",
            "Isotherm",
            "Rainbow",
            "Sepia",
        )
    private val palettes =
        arrayOf<Array<IntArray?>?>(
            Arctic.palette,
            Banded.palette,
            Blackhot.pallete,
            DoubleRainbow.palette,
            Fusion.palette,
            Gray.palette,
            Ironblack.palette,
            Isotherm.palette,
            Rainbow.palette,
            Sepia.palette,
        )

    fun getPaletteName(index: Int): String? {
        if (index < paletteNames.size) {
            return paletteNames[index]
        } else {
            return null
        }
    }

    fun getPaletteByName(name: String?): Array<IntArray?>? {
        for (index in paletteNames.indices) {
            if (paletteNames[index].equals(name, ignoreCase = true)) {
                return palettes[index]
            }
        }
        return null
    }
}

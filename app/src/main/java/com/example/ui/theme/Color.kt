package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val MidnightBg = Color(0xFF0B101B)
val ObsidianSurface = Color(0xFF131B2E)
val SlateSurfaceVariant = Color(0xFF1C2640)
val BorderSubtle = Color(0xFF283554)

val BoardLightBg = Color(0xFFF1F5F9)
val BoardCellLight = Color(0xFFF8FAFC)
val BoardGridLine = Color(0xFFCBD5E1)
val BoardDangerTint = Color(0xFFFEE2E2)

val CyanElectric = Color(0xFF22D3EE)
val CyanDeep = Color(0xFF0891B2)
val AmberWarm = Color(0xFFFBBF24)
val AmberDeep = Color(0xFFD97706)
val EmeraldMint = Color(0xFF34D399)
val EmeraldDeep = Color(0xFF059669)
val CoralRose = Color(0xFFFB7185)
val CoralDeep = Color(0xFFE11D48)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val BlockInkDark = Color(0xFF0F172A)

data class TilePalette(
    val fill: Color,
    val border: Color,
    val highlight: Color,
    val content: Color
)

// Paleta de colores claros y luminosos para bloques con texto oscuro de alto contraste
private val FactrisPaletteList = listOf(
    TilePalette(Color(0xFFBAE6FD), Color(0xFF0284C7), Color(0xFFE0F2FE), BlockInkDark), // Sky claro
    TilePalette(Color(0xFFA7F3D0), Color(0xFF059669), Color(0xFFD1FAE5), BlockInkDark), // Menta claro
    TilePalette(Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFFFEF3C7), BlockInkDark), // Ámbar claro
    TilePalette(Color(0xFFDDD6FE), Color(0xFF7C3AED), Color(0xFFEDE9FE), BlockInkDark), // Lavanda claro
    TilePalette(Color(0xFFFBCFE8), Color(0xFFDB2777), Color(0xFFFCE7F3), BlockInkDark), // Rosa claro
    TilePalette(Color(0xFF99F6E4), Color(0xFF0D9488), Color(0xFFCCFBF1), BlockInkDark), // Turquesa claro
    TilePalette(Color(0xFFFED7AA), Color(0xFFEA580C), Color(0xFFFFEDD5), BlockInkDark), // Melocotón claro
    TilePalette(Color(0xFFBFDBFE), Color(0xFF2563EB), Color(0xFFDBEAFE), BlockInkDark), // Azul claro
    TilePalette(Color(0xFFFECDD3), Color(0xFFE11D48), Color(0xFFFFE4E6), BlockInkDark), // Coral claro
    TilePalette(Color(0xFFD9F99D), Color(0xFF65A30D), Color(0xFFECFCCB), BlockInkDark), // Lima claro
    TilePalette(Color(0xFFA5F3FC), Color(0xFF0891B2), Color(0xFFCFFAFE), BlockInkDark), // Cian claro
    TilePalette(Color(0xFFE9D5FF), Color(0xFF9333EA), Color(0xFFF3E8FF), BlockInkDark)  // Lila claro
)

fun tilePaletteFor(areaValue: Int): TilePalette {
    val index = ((areaValue * 7) % FactrisPaletteList.size).let { if (it < 0) it + FactrisPaletteList.size else it }
    return FactrisPaletteList[index]
}

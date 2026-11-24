package com.morningboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** 문자 그리드로 그린 픽셀 아이콘. '.' = 투명 */
object Pixel {
    private val palette = mapOf(
        'K' to Color(0xFF1E1E2E), // 외곽선
        'W' to Color(0xFFF8F9FA), // 흰색
        'G' to Color(0xFFBAC0CC), // 회색
        'D' to Color(0xFF868E9C), // 진회색
        'Y' to Color(0xFFFFD43B), // 노랑
        'O' to Color(0xFFF59F00), // 주황
        'B' to Color(0xFF339AF0), // 파랑(빗방울)
        'N' to Color(0xFF2B3A67), // 남색(롱패딩)
        'L' to Color(0xFF4A5C9E), // 남색 밝은 퀼팅
        'R' to Color(0xFFE8590C), // 주황빨강(숏패딩)
        'r' to Color(0xFFFF922B), // 숏패딩 퀼팅
        'H' to Color(0xFF2F9E44), // 후디 초록
        'h' to Color(0xFF51CF66), // 후디 밝은
        'P' to Color(0xFF7048E8), // 맨투맨 보라
        'p' to Color(0xFF9775FA),
        'T' to Color(0xFF22B8CF), // 반팔 하늘
        't' to Color(0xFF66D9E8),
        'J' to Color(0xFF1864AB), // 청바지
        'j' to Color(0xFF4DABF7),
        'U' to Color(0xFFFA5252), // 우산 빨강
        'u' to Color(0xFFFF8787),
        'M' to Color(0xFF8D6E63), // 손잡이 갈색
    )

    fun colorFor(ch: Char): Color? = palette[ch]

    val SUN = arrayOf(
        "................",
        ".......YY.......",
        "..Y....YY....Y..",
        "...Y........Y...",
        "......OOOO......",
        ".....OYYYYO.....",
        "....OYYYYYYO....",
        "YY..OYYYYYYO..YY",
        "YY..OYYYYYYO..YY",
        "....OYYYYYYO....",
        ".....OYYYYO.....",
        "......OOOO......",
        "...Y........Y...",
        "..Y....YY....Y..",
        ".......YY.......",
        "................",
    )

    val MOON = arrayOf(
        "................",
        ".......OOOO.....",
        ".....OOYYO......",
        "....OYYYO.......",
        "...OYYYO........",
        "...OYYO.........",
        "..OYYYO.........",
        "..OYYYO.........",
        "..OYYYO.........",
        "..OYYYYO........",
        "...OYYYYO.....O.",
        "...OYYYYYOOOOO..",
        "....OYYYYYYYYO..",
        ".....OOYYYYOO...",
        ".......OOOO.....",
        "................",
    )

    val CLOUD = arrayOf(
        "................",
        "................",
        "................",
        "......KKKK......",
        ".....KWWWWK.....",
        "...KKWWWWWWK....",
        "..KWWWWWWWWWKK..",
        ".KWWWWWWWWWWWWK.",
        "KWWWWWWWWWWWWWWK",
        "KWWWWWWWWWWWWWWK",
        "KGWWWWWWWWWWWWGK",
        ".KGGGGGGGGGGGGK.",
        "..KKKKKKKKKKKK..",
        "................",
        "................",
        "................",
    )

    val PARTLY = arrayOf(
        "..Y.....Y.......",
        "...Y...Y........",
        ".....OOOO.......",
        "YY..OYYYYO..YY..",
        "...OYYYYYYO.....",
        "...OYYYKKKK.....",
        "...OYYKWWWWK....",
        "....OKWWWWWWKK..",
        "..YKKWWWWWWWWWK.",
        ".YKWWWWWWWWWWWWK",
        "..KWWWWWWWWWWWWK",
        "..KGWWWWWWWWWWGK",
        "...KGGGGGGGGGGK.",
        "....KKKKKKKKKK..",
        "................",
        "................",
    )

    val RAIN = arrayOf(
        "................",
        "......KKKK......",
        ".....KGGGGK.....",
        "...KKGGGGGGK....",
        "..KGGGGGGGGGKK..",
        ".KGGGGGGGGGGGGK.",
        "KGGGGGGGGGGGGGGK",
        "KDGGGGGGGGGGGGDK",
        ".KDDDDDDDDDDDDK.",
        "..KKKKKKKKKKKK..",
        "................",
        "...B....B....B..",
        "..B....B....B...",
        "................",
        ".B....B....B....",
        "B....B....B.....",
    )

    val SNOW = arrayOf(
        "................",
        "......KKKK......",
        ".....KWWWWK.....",
        "...KKWWWWWWK....",
        "..KWWWWWWWWWKK..",
        ".KWWWWWWWWWWWWK.",
        "KWWWWWWWWWWWWWWK",
        "KGWWWWWWWWWWWWGK",
        ".KGGGGGGGGGGGGK.",
        "..KKKKKKKKKKKK..",
        "................",
        "..B....B....B...",
        ".BWB..BWB..BWB..",
        "..B....B....B...",
        "....B....B......",
        "...BWB..BWB.....",
    )

    val THUNDER = arrayOf(
        "................",
        "......KKKK......",
        ".....KDDDDK.....",
        "...KKDDDDDDK....",
        "..KDDDDDDDDDKK..",
        ".KDDDDDDDDDDDDK.",
        "KDDDDDDDDDDDDDDK",
        "KDDDDDDDDDDDDDDK",
        ".KDDDDDOYDDDDDK.",
        "..KKKKOYKKKKKK..",
        "......OYY.......",
        ".....OYYYY......",
        ".......OY.......",
        "......OY........",
        "......Y.........",
        "................",
    )

    val FOG = arrayOf(
        "................",
        "................",
        "................",
        "..GGGGGGGGGG....",
        "................",
        "....GGGGGGGGGGG.",
        "................",
        ".GGGGGGGGGGGG...",
        "................",
        "...GGGGGGGGGGGG.",
        "................",
        "..GGGGGGGGGG....",
        "................",
        "................",
        "................",
        "................",
    )

    val LONG_PADDING = arrayOf(
        "....KKKKKKKK....",
        "...KNNNNNNNNK...",
        "..KNNKKKKKKNNK..",
        "..KNK......KNK..",
        ".KNNNKKKKKKNNNK.",
        "KNLLLLLNNLLLLLNK",
        "KNNNNNNNNNNNNNNK",
        "KNLKLLLNNLLLKLNK",
        "KNNKNNNNNNNNKNNK",
        "KNLKLLLNNLLLKLNK",
        "KKKKNNNNNNNNKKKK",
        "...KLLLNNLLLK...",
        "...KNNNNNNNNK...",
        "...KLLLNNLLLK...",
        "...KNNNNNNNNK...",
        "...KKKKKKKKKK...",
    )

    val SHORT_PADDING = arrayOf(
        "................",
        "....KKKKKKKK....",
        "...KRRRRRRRRK...",
        "..KRRKKKKKKRRK..",
        ".KRRRK....KRRRK.",
        "KRrrrrrRRrrrrrRK",
        "KRRRRRRRRRRRRRRK",
        "KRrKrrrRRrrrKrRK",
        "KRRKRRRRRRRRKRRK",
        "KRrKrrrRRrrrKrRK",
        "KRRKRRRRRRRRKRRK",
        "KKKKRRRRRRRRKKKK",
        "...KKKKKKKKKK...",
        "................",
        "................",
        "................",
    )

    val HOODIE = arrayOf(
        "................",
        ".....KKKKKK.....",
        "....KHHHHHHK....",
        "...KHHKKKKHHK...",
        "..KHHK....KHHK..",
        ".KHHHHKKKKHHHHK.",
        "KHHHHHHWHHWHHHHK",
        "KHHKHHHWHHWHKHHK",
        "KHHKHHHHHHHHKHHK",
        "KHHKHhhhhhhHKHHK",
        "KHHKHhHHHHhHKHHK",
        "KKKKHhhhhhhHKKKK",
        "...KHHHHHHHHK...",
        "...KKKKKKKKKK...",
        "................",
        "................",
    )

    val SWEATSHIRT = arrayOf(
        "................",
        "................",
        "....KKK..KKK....",
        "..KKPPPKKPPPKK..",
        ".KPPPPPPPPPPPPK.",
        "KPPPPPPPPPPPPPPK",
        "KPPKPPPPPPPPKPPK",
        "KPPKPPPPPPPPKPPK",
        "KPPKPPPPPPPPKPPK",
        "KPPKPPPPPPPPKPPK",
        "KppKPPPPPPPPKppK",
        "KKKKpppppppppKKK",
        "...KKKKKKKKKK...",
        "................",
        "................",
        "................",
    )

    val TSHIRT = arrayOf(
        "................",
        "................",
        "................",
        "....KKK..KKK....",
        "..KKTTTKKTTTKK..",
        ".KTTTTTTTTTTTTK.",
        "KTTTTTTTTTTTTTTK",
        "KttKTTTTTTTTKttK",
        ".KKKTTTTTTTTKKK.",
        "...KTTTTTTTTK...",
        "...KTTTTTTTTK...",
        "...KTTTTTTTTK...",
        "...KKKKKKKKKK...",
        "................",
        "................",
        "................",
    )

    val SHORTS = arrayOf(
        "................",
        "................",
        "................",
        "................",
        "...KKKKKKKKKK...",
        "...KJJJJJJJJK...",
        "...KjjjjjjjjK...",
        "..KJJJJJJJJJJK..",
        "..KJJJJKKJJJJK..",
        "..KJJJJKKJJJJK..",
        ".KJJJJK..KJJJJK.",
        ".KKKKKK..KKKKKK.",
        "................",
        "................",
        "................",
        "................",
    )

    val PANTS = arrayOf(
        "................",
        "....KKKKKKKK....",
        "....KJJJJJJK....",
        "....KjjjjjjK....",
        "...KJJJJJJJJK...",
        "...KJJJKKJJJK...",
        "...KJJJKKJJJK...",
        "...KJJJKKJJJK...",
        "..KJJJK..KJJJK..",
        "..KJJJK..KJJJK..",
        "..KJJJK..KJJJK..",
        "..KJJJK..KJJJK..",
        "..KJJJK..KJJJK..",
        "..KjjjK..KjjjK..",
        "..KKKKK..KKKKK..",
        "................",
    )

    val UMBRELLA = arrayOf(
        ".......KK.......",
        ".....KKUUKK.....",
        "...KKUUuUUUKK...",
        "..KUUUuUUUuUUK..",
        ".KUUUuUUUUUuUUK.",
        ".KUUuUUUUUUUuUK.",
        "KUUUuUUUUUUUuUUK",
        "KKKKKKKMMKKKKKKK",
        ".......MM.......",
        ".......MM.......",
        ".......MM.......",
        ".......MM.......",
        ".......MM.......",
        "...MM..MM.......",
        "...MMMMMM.......",
        "....MMMM........",
    )
}

/**
 * 픽셀 스프라이트를 Compose Canvas 로 그린다 (번짐 없음).
 * tileBg 가 null 이 아니면 밝은 둥근 타일 위에 아이콘을 얹고, null 이면 아이콘만 그린다.
 */
@Composable
fun PixelSprite(
    sprite: Array<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    tileBg: Color? = Color(0xFFE9EDF3),
) {
    Canvas(modifier.size(size)) {
        val px = this.size.minDimension
        if (tileBg != null) {
            val r = px * 0.18f
            drawRoundRect(color = tileBg, size = Size(px, px), cornerRadius = CornerRadius(r, r))
        }
        val cols = sprite.maxOf { it.length }
        val rowsN = sprite.size
        val grid = maxOf(cols, rowsN).coerceAtLeast(1)
        val fill = if (tileBg != null) 0.72f else 1.0f
        val cell = (px * fill) / grid
        val offX = (px - cell * cols) / 2f
        val offY = (px - cell * rowsN) / 2f
        for (y in sprite.indices) {
            val line = sprite[y]
            for (x in line.indices) {
                val c = Pixel.colorFor(line[x]) ?: continue
                drawRect(
                    color = c,
                    topLeft = Offset(offX + x * cell, offY + y * cell),
                    size = Size(cell + 0.6f, cell + 0.6f), // 셀 사이 틈 방지
                )
            }
        }
    }
}

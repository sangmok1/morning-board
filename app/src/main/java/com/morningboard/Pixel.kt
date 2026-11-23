package com.morningboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** 문자 그리드로 그린 픽셀 아이콘. '.' = 투명 */
object Pixel {
    private val palette = mapOf(
        'K' to 0xFF1E1E2E.toInt(), // 외곽선
        'W' to 0xFFF8F9FA.toInt(), // 흰색
        'G' to 0xFFBAC0CC.toInt(), // 회색
        'D' to 0xFF868E9C.toInt(), // 진회색
        'Y' to 0xFFFFD43B.toInt(), // 노랑
        'O' to 0xFFF59F00.toInt(), // 주황
        'B' to 0xFF339AF0.toInt(), // 파랑(빗방울)
        'N' to 0xFF2B3A67.toInt(), // 남색(롱패딩)
        'L' to 0xFF4A5C9E.toInt(), // 남색 밝은 퀼팅
        'R' to 0xFFE8590C.toInt(), // 주황빨강(숏패딩)
        'r' to 0xFFFF922B.toInt(), // 숏패딩 퀼팅
        'H' to 0xFF2F9E44.toInt(), // 후디 초록
        'h' to 0xFF51CF66.toInt(), // 후디 밝은
        'P' to 0xFF7048E8.toInt(), // 맨투맨 보라
        'p' to 0xFF9775FA.toInt(),
        'T' to 0xFF22B8CF.toInt(), // 반팔 하늘
        't' to 0xFF66D9E8.toInt(),
        'J' to 0xFF1864AB.toInt(), // 청바지
        'j' to 0xFF4DABF7.toInt(),
        'U' to 0xFFFA5252.toInt(), // 우산 빨강
        'u' to 0xFFFF8787.toInt(),
        'M' to 0xFF8D6E63.toInt(), // 손잡이 갈색
    )

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

    /** 픽셀 단위로 정확히 확대한 비트맵 (번짐 없음) */
    fun bitmap(sprite: Array<String>, scale: Int): Bitmap {
        val h = sprite.size
        val w = sprite.maxOf { it.length }
        val bmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint()
        for (y in 0 until h) for (x in sprite[y].indices) {
            val color = palette[sprite[y][x]] ?: continue
            paint.color = color
            c.drawRect(
                (x * scale).toFloat(), (y * scale).toFloat(),
                ((x + 1) * scale).toFloat(), ((y + 1) * scale).toFloat(), paint
            )
        }
        return bmp
    }

    /** 밝은 둥근 타일 위에 아이콘 */
    fun tile(sprite: Array<String>, sizePx: Int, bg: Int = 0xFFE9EDF3.toInt()): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        val r = sizePx * 0.18f
        c.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), r, r, p)
        val w = sprite.maxOf { it.length }
        val scale = maxOf(1, (sizePx * 0.72f / maxOf(w, sprite.size)).toInt())
        val icon = bitmap(sprite, scale)
        c.drawBitmap(icon, ((sizePx - icon.width) / 2).toFloat(), ((sizePx - icon.height) / 2).toFloat(), null)
        return bmp
    }

    /** 타일 여러 개를 가로로 이어붙임 (위젯용) */
    fun row(sprites: List<Array<String>>, tilePx: Int, gapPx: Int): Bitmap {
        val n = maxOf(1, sprites.size)
        val bmp = Bitmap.createBitmap(n * tilePx + (n - 1) * gapPx, tilePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bmp)
        sprites.forEachIndexed { i, s -> c.drawBitmap(tile(s, tilePx), (i * (tilePx + gapPx)).toFloat(), 0f, null) }
        return bmp
    }
}

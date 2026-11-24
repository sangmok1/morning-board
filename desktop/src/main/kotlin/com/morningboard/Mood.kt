package com.morningboard

/** 날씨 분위기 */
enum class Mood { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, RAIN, SNOW, FOG, THUNDER }

fun moodOf(code: Int, isDay: Boolean): Mood = when (code) {
    0, 1 -> if (isDay) Mood.CLEAR_DAY else Mood.CLEAR_NIGHT
    2 -> if (isDay) Mood.CLEAR_DAY else Mood.CLEAR_NIGHT
    3 -> Mood.CLOUDY
    45, 48 -> Mood.FOG
    in 71..77, 85, 86 -> Mood.SNOW
    in 95..99 -> Mood.THUNDER
    in 51..67, in 80..82 -> Mood.RAIN
    else -> Mood.CLOUDY
}

package org.eshragh.kartext.utils

import java.util.Date
import java.util.Calendar

data class ShamsiDate(val year: Int, val month: Int, val day: Int)

object DateConverter {

    fun toPersianNumbers(text: String): String {
        val persianNumbers = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val builder = StringBuilder()
        for (char in text) {
            if (char in '0'..'9') {
                builder.append(persianNumbers[char.toString().toInt()])
            } else {
                builder.append(char)
            }
        }
        return builder.toString()
    }

    fun toShamsi(date: Date): ShamsiDate {
        val cal = Calendar.getInstance()
        cal.time = date
        var gy = cal.get(Calendar.YEAR)
        var gm = cal.get(Calendar.MONTH) + 1
        var gd = cal.get(Calendar.DAY_OF_MONTH)

        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]

        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461

        if (days > 365) {
            jy += ((days - 1) / 365)
            days = (days - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return ShamsiDate(jy.toInt(), jm, jd)
    }

    fun getShamsiMonthName(month: Int): String {
        return when (month) {
            1 -> "فروردین"
            2 -> "اردیبهشت"
            3 -> "خرداد"
            4 -> "تیر"
            5 -> "مرداد"
            6 -> "شهریور"
            7 -> "مهر"
            8 -> "آبان"
            9 -> "آذر"
            10 -> "دی"
            11 -> "بهمن"
            12 -> "اسفند"
            else -> ""
        }
    }

    fun getShamsiDayOfWeek(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }
    }

    fun formatShamsiDate(shamsiDate: ShamsiDate): String {
        return String.format("%d/%02d/%02d", shamsiDate.year, shamsiDate.month, shamsiDate.day)
    }
}
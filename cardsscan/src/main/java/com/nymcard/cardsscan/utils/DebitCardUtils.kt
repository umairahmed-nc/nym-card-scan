package com.nymcard.cardsscan.utils

object DebitCardUtils {
    fun getBankSlugFromCardNumber(cardNumber: String): String? {
        if (cardNumber.length < 6) return null

        return cardNumber.substring(0, 6)
    }

    @JvmStatic
    fun luhnCheck(ccNumber: String?): Boolean {
        if (ccNumber == null || ccNumber.length != 16 || getBankSlugFromCardNumber(ccNumber) == null) {
            return false
        }

        var sum = 0
        var alternate = false
        for (i in ccNumber.length - 1 downTo 0) {
            var n = ccNumber.substring(i, i + 1).toInt()
            if (alternate) {
                n *= 2
                if (n > 9) {
                    n = (n % 10) + 1
                }
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }

    @JvmStatic
    fun format(number: String): String {
        if (number.length == 16) {
            return format16(number)
        }

        return number
    }

    private fun format16(number: String): String {
        val result = StringBuilder()
        for (i in 0..<number.length) {
            if (i == 4 || i == 8 || i == 12) {
                result.append(" ")
            }
            result.append(number.get(i))
        }

        return result.toString()
    }
}

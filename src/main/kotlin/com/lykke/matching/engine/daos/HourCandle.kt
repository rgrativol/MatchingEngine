package com.lykke.matching.engine.daos

import java.util.LinkedList

class HourCandle(val asset: String, val prices: LinkedList<Double>) {

    fun addPrice(price: Double?) {
        if (price != null) {
            prices.add(price)
            while (prices.size < 20) {
                prices.add(price)
            }
            if (prices.size > 20) {
                prices.removeFirst()
            }
        }
    }
}
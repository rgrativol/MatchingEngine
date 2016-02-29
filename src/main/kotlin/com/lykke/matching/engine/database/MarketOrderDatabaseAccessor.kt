package com.lykke.matching.engine.database

import com.lykke.matching.engine.daos.MarketOrder
import com.lykke.matching.engine.daos.Trade

interface MarketOrderDatabaseAccessor {
    fun addMarketOrder(order: MarketOrder)
    fun updateMarketOrder(order: MarketOrder)

    fun addTrades(trades: List<Trade>)
}
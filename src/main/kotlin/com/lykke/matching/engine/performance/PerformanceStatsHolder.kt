package com.lykke.matching.engine.performance

import java.util.HashMap

class PerformanceStatsHolder {

    private var statsMap = HashMap<Byte, PerformanceStats>()

    fun addMessage(type: Byte, totalTime: Long, processingTime: Long) {
        val stats = statsMap.getOrPut(type) { PerformanceStats(type, totalTime, processingTime, 0) }
        stats.totalTime += totalTime
        stats.processingTime += processingTime
        stats.count++
    }

    fun getStatsAndReset(): Map<Byte, PerformanceStats> {
        val result = statsMap
        statsMap = HashMap()
        return result
    }
}
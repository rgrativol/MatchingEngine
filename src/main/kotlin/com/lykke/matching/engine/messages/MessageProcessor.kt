package com.lykke.matching.engine.messages

import com.lykke.matching.engine.database.LimitOrderDatabaseAccessor
import com.lykke.matching.engine.database.MarketOrderDatabaseAccessor
import com.lykke.matching.engine.database.WalletDatabaseAccessor
import com.lykke.matching.engine.database.azure.AzureLimitOrderDatabaseAccessor
import com.lykke.matching.engine.database.azure.AzureMarketOrderDatabaseAccessor
import com.lykke.matching.engine.database.azure.AzureWalletDatabaseAccessor
import com.lykke.matching.engine.services.CashOperationService
import com.lykke.matching.engine.services.LimitOrderService
import com.lykke.matching.engine.services.MarketOrderService
import org.apache.log4j.Logger
import java.util.Properties
import java.util.concurrent.BlockingQueue

class MessageProcessor: Thread {

    companion object {
        val LOGGER = Logger.getLogger(MessageProcessor::class.java.name)
    }

    val messagesQueue: BlockingQueue<MessageWrapper>

    val walletDatabaseAccessor: WalletDatabaseAccessor
    val limitOrderDatabaseAccessor: LimitOrderDatabaseAccessor
    val marketOrderDatabaseAccessor: MarketOrderDatabaseAccessor

    val cashOperationService: CashOperationService
    val limitOrderService: LimitOrderService
    val marketOrderService: MarketOrderService

    constructor(config: Properties, queue: BlockingQueue<MessageWrapper>) {
        this.messagesQueue = queue
        this.walletDatabaseAccessor = AzureWalletDatabaseAccessor(config)
        this.limitOrderDatabaseAccessor = AzureLimitOrderDatabaseAccessor(config)
        this.marketOrderDatabaseAccessor = AzureMarketOrderDatabaseAccessor(config)

        this.cashOperationService = CashOperationService(this.walletDatabaseAccessor)
        this.limitOrderService = LimitOrderService(this.limitOrderDatabaseAccessor, cashOperationService)
        this.marketOrderService = MarketOrderService(this.marketOrderDatabaseAccessor, limitOrderService, cashOperationService)
    }

    override fun run() {
        while (true) {
            processMessage(messagesQueue.take())
        }
    }

    private fun processMessage(message: MessageWrapper) {
        when (message.type) {
            //MessageType.PING -> already processed by client handler
            MessageType.UPDATE_BALANCE -> {
                cashOperationService.processMessage(message.byteArray)
            }
            MessageType.LIMIT_ORDER -> {
                limitOrderService.processMessage(message.byteArray)
            }
            MessageType.LIMIT_ORDER -> {
                marketOrderService.processMessage(message.byteArray)
            }
            else -> {
                LOGGER.error("Unknown message type: ${message.type}")
            }
        }
    }
}
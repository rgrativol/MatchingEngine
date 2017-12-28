package com.lykke.matching.engine.services

import com.lykke.matching.engine.daos.Asset
import com.lykke.matching.engine.database.TestBackOfficeDatabaseAccessor
import com.lykke.matching.engine.database.TestCashOperationsDatabaseAccessor
import com.lykke.matching.engine.database.TestWalletDatabaseAccessor
import com.lykke.matching.engine.database.buildWallet
import com.lykke.matching.engine.database.cache.AssetsCache
import com.lykke.matching.engine.holders.AssetsHolder
import com.lykke.matching.engine.holders.BalancesHolder
import com.lykke.matching.engine.messages.MessageType
import com.lykke.matching.engine.messages.MessageWrapper
import com.lykke.matching.engine.messages.ProtocolMessages
import com.lykke.matching.engine.notification.BalanceUpdateNotification
import com.lykke.matching.engine.outgoing.messages.CashOperation
import com.lykke.matching.engine.outgoing.messages.JsonSerializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

class CashOperationServiceTest {

    val testWalletDatabaseAccessor = TestWalletDatabaseAccessor()
    val testCashOperationsDatabaseAccessor = TestCashOperationsDatabaseAccessor()
    var testBackOfficeDatabaseAcessor = TestBackOfficeDatabaseAccessor()
    val transactionQueue = LinkedBlockingQueue<JsonSerializable>()
    val balanceNotificationQueue = LinkedBlockingQueue<BalanceUpdateNotification>()
    val balanceUpdateQueue = LinkedBlockingQueue<JsonSerializable>()
    val assetsHolder = AssetsHolder(AssetsCache(testBackOfficeDatabaseAcessor, 60000))
    lateinit var balancesHolder: BalancesHolder
    lateinit var service: CashInOutOperationService
    val DELTA = 1e-15

    @Before
    fun setUp() {
        testWalletDatabaseAccessor.clear()
        testBackOfficeDatabaseAcessor.addAsset(Asset("Asset1", 2))
        testBackOfficeDatabaseAcessor.addAsset(Asset("Asset2", 2))
        testBackOfficeDatabaseAcessor.addAsset(Asset("Asset3", 2))
        testBackOfficeDatabaseAcessor.addAsset(Asset("Asset4", 2))
        testBackOfficeDatabaseAcessor.addAsset(Asset("Asset5", 8))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client1", "Asset1", 100.0))
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client2", "Asset1", 100.0))
        transactionQueue.clear()
        initService()
    }

    private fun initService() {
        balancesHolder = BalancesHolder(testWalletDatabaseAccessor, assetsHolder, balanceNotificationQueue, balanceUpdateQueue, emptySet())
        service = CashInOutOperationService(testCashOperationsDatabaseAccessor, assetsHolder, balancesHolder, transactionQueue)
    }

    @Test
    fun testCashIn() {
        service.processMessage(buildBalanceWrapper("Client1", "Asset1", 50.0))
        val balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")
        assertNotNull(balance)
        assertEquals(150.0, balance, DELTA)

        val cashInTransaction = transactionQueue.take() as CashOperation
        assertEquals("Client1", cashInTransaction.clientId)
        assertEquals("50.00", cashInTransaction.volume)
        assertEquals("Asset1", cashInTransaction.asset)
    }

    @Test
    fun testSmallCashIn() {
        service.processMessage(buildBalanceWrapper("Client1", "Asset1", 0.01))
        val balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")
        assertNotNull(balance)
        assertEquals(100.01, balance, DELTA)

        val cashInTransaction = transactionQueue.take() as CashOperation
        assertEquals("Client1", cashInTransaction.clientId)
        assertEquals("0.01", cashInTransaction.volume)
        assertEquals("Asset1", cashInTransaction.asset)
    }

    @Test
    fun testCashOut() {
        service.processMessage(buildBalanceWrapper("Client1", "Asset1", -50.0))
        val balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")
        assertNotNull(balance)
        assertEquals(50.0, balance, DELTA)

        val cashOutTransaction = transactionQueue.take() as CashOperation
        assertEquals("Client1", cashOutTransaction.clientId)
        assertEquals("-50.00", cashOutTransaction.volume)
        assertEquals("Asset1", cashOutTransaction.asset)
    }

    @Test
    fun testCashOutNegative() {
        service.processMessage(buildBalanceWrapper("Client1", "Asset1", -50.0))
        var balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")
        assertNotNull(balance)
        assertEquals(50.0, balance, DELTA)

        val cashOutTransaction = transactionQueue.take() as CashOperation
        assertEquals("Client1", cashOutTransaction.clientId)
        assertEquals("-50.00", cashOutTransaction.volume)
        assertEquals("Asset1", cashOutTransaction.asset)

        service.processMessage(buildBalanceWrapper("Client1", "Asset1", -60.0))
        balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")
        assertEquals(50.0, balance, DELTA)
    }

    @Test
    fun testAddNewAsset() {
        service.processMessage(buildBalanceWrapper("Client1", "Asset4", 100.0))
        val balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset4")

        assertNotNull(balance)
        assertEquals(100.0, balance, DELTA)
    }

    @Test
    fun testAddNewWallet() {
        service.processMessage(buildBalanceWrapper("Client3", "Asset2", 100.0))
        val balance = testWalletDatabaseAccessor.getBalance("Client3", "Asset2")

        assertNotNull(balance)
        assertEquals(100.0, balance, DELTA)
    }

    @Test
    fun testUpdateBalance() {
        val updateService = BalanceUpdateService(balancesHolder)

        updateService.processMessage(buildBalanceUpdateWrapper("Client1", "Asset1", 999.0))

        val balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")
        assertNotNull(balance)
        assertEquals(999.0, balance, DELTA)

    }

    @Test
    fun testRounding() {
        val updateService = BalanceUpdateService(balancesHolder)

        updateService.processMessage(buildBalanceUpdateWrapper("Client1", "Asset1", 29.99))
        service.processMessage(buildBalanceWrapper("Client1", "Asset1", -0.01))

        val balance = testWalletDatabaseAccessor.getBalance("Client1", "Asset1")

        assertNotNull(balance)

        assertEquals("29.98", balance.toString())
    }

    @Test
    fun testRoundingWithReserved() {
        testWalletDatabaseAccessor.insertOrUpdateWallet(buildWallet("Client1", "Asset5", 1.00418803, 0.00418803))
        initService()

        service.processMessage(buildBalanceWrapper("Client1", "Asset5", -1.0))

        assertEquals(1, transactionQueue.size)
        val cashInTransaction = transactionQueue.peek() as CashOperation
        assertNotNull(cashInTransaction)
        assertEquals("Client1", cashInTransaction.clientId)
        assertEquals("-1.00000000", cashInTransaction.volume)
        assertEquals("Asset5", cashInTransaction.asset)

    }

    private fun buildBalanceWrapper(clientId: String, assetId: String, amount: Double, bussinesId: String = UUID.randomUUID().toString()): MessageWrapper {
        return MessageWrapper("Test", MessageType.CASH_IN_OUT_OPERATION.type, ProtocolMessages.CashInOutOperation.newBuilder()
                .setId(bussinesId)
                .setClientId(clientId)
                .setAssetId(assetId)
                .setVolume(amount)
                .setTimestamp(Date().time).build().toByteArray(), null)
    }

    private fun buildBalanceUpdateWrapper(clientId: String, assetId: String, amount: Double): MessageWrapper {
        return MessageWrapper("Test", MessageType.OLD_BALANCE_UPDATE.type, ProtocolMessages.OldBalanceUpdate.newBuilder()
                .setUid(123)
                .setClientId(clientId)
                .setAssetId(assetId)
                .setAmount(amount).build().toByteArray(), null)
    }
}
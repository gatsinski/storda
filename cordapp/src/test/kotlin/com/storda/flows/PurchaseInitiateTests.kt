package com.storda.flows

import com.storda.PurchaseState
import net.corda.core.node.services.queryBy
import net.corda.finance.POUNDS
import org.junit.Test
import kotlin.test.assertEquals

class PurchaseInitiateTests : PurchaseTestsBase() {

    @Test
    fun `Purchase should be initiated successfully`() {
        val signedTx = initiatePurchase(buyerNode, sellerNode, 10.POUNDS, 1)
        network.waitQuiescent()

        val buyerPurchaseState = buyerNode.services.loadState(
            signedTx.tx.outRef<PurchaseState>(0).ref
        ).data as PurchaseState

        val sellerPurchaseState = sellerNode.services.loadState(
            signedTx.tx.outRef<PurchaseState>(0).ref
        ).data as PurchaseState

        assertEquals(buyerPurchaseState, sellerPurchaseState)
    }


    @Test
    fun `Purchase should be recorded by both parties`() {
        val price = 10.POUNDS
        val itemId = 1
        initiatePurchase(buyerNode, sellerNode, price, itemId)
        network.waitQuiescent()

        for (node in listOf(buyerNode, sellerNode)) {
            node.transaction {
                val purchases = node.services.vaultService.queryBy<PurchaseState>().states

                assertEquals(purchases.size, 1)

                val purchase = purchases.single().state.data

                assertEquals(10.POUNDS, purchase.price, "Price should be recorded")
                assertEquals(1, purchase.itemId, "Item id should be recorded")
                assertEquals(buyer, purchase.buyer, "Buyer should be recorded")
                assertEquals(seller, purchase.seller, "Seller should be recorded")
            }
        }
    }

    @Test
    fun `PurchaseInitiateFlow should return transaction signed by both parties`() {
        val signedTransaction = initiatePurchase(buyerNode, sellerNode, 10.POUNDS, 1)
        network.waitQuiescent()

        val transactionSignatures = signedTransaction.sigs.map { it.by }.toSet()
        val expectedSignatures = listOf(buyer, seller).map { it.owningKey }.toSet()

        assertEquals(expectedSignatures, transactionSignatures, "Both parties should sign")
    }
}

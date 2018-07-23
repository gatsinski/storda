package com.storda.flows

import com.storda.PurchaseState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class PurchaseInitiateTests {
    private lateinit var network: MockNetwork
    private lateinit var buyerNode: StartedMockNode
    private lateinit var sellerNode: StartedMockNode
    private lateinit var buyer: Party
    private lateinit var seller: Party

    @Before
    fun setup() {
        network = MockNetwork(listOf("com.storda"), threadPerNode = true)
        buyerNode = network.createNode()
        sellerNode = network.createNode()
        buyer = buyerNode.info.singleIdentity()
        seller = sellerNode.info.singleIdentity()
        listOf(buyerNode, sellerNode).forEach {
            it.registerInitiatedFlow(PurchaseInitiateFlow.Responder::class.java)
        }
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


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

                requireThat {
                    "Price should be recorded" using (purchase.price == 10.POUNDS)
                    "Item id should recorded" using (purchase.itemId == 1)
                    "Buyer should be recorded" using (purchase.buyer == buyer)
                    "Seller should be recorded" using (purchase.seller == seller)
                }
            }
        }
    }

    @Test
    fun `PurchaseInitiateFlow should return transaction signed by both parties`() {
        val signedTransaction = initiatePurchase(buyerNode, sellerNode, 10.POUNDS, 1)
        network.waitQuiescent()

        val transactionSignatures = signedTransaction.sigs.map { it.by }.toSet()
        val expectedSignatures = listOf(buyer, seller).map { it.owningKey }.toSet()

        requireThat {
            "Both parties should sign" using (transactionSignatures == expectedSignatures)
        }
    }

    private fun initiatePurchase(
        buyer: StartedMockNode,
        seller: StartedMockNode,
        price: Amount<Currency>,
        itemId: Int
    ): SignedTransaction {
        val sellerIdentity = seller.info.singleIdentity()
        val flow = PurchaseInitiateFlow.Initiator(sellerIdentity, price, itemId)
        return buyer.startFlow(flow).getOrThrow()
    }
}

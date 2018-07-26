package com.storda.flows

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.util.*

open class PurchaseTestsBase{
    protected lateinit var network: MockNetwork
    protected lateinit var buyerNode: StartedMockNode
    protected lateinit var sellerNode: StartedMockNode
    protected lateinit var buyer: Party
    protected lateinit var seller: Party

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

    protected fun initiatePurchase(
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

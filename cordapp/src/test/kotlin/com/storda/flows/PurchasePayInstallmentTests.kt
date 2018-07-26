package com.storda.flows

import com.storda.PurchaseState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PurchasePayInstallmentTests : PurchaseTestsBase() {

    @Test
    fun `Installments should be paid successfully`() {
        val installment = 10.POUNDS
        val initiateTransaction = initiatePurchase(buyerNode, sellerNode, 100.POUNDS, 1)
        network.waitQuiescent()
        val initiatedPurchaseState = initiateTransaction.tx.outputStates.single() as PurchaseState

        // First installment payment
        val firstPaymentPurchaseState = payInstallmentAndGetState(
            linearId = initiatedPurchaseState.linearId,
            amount = installment,
            buyer = buyerNode
        )
        assertEquals(installment, firstPaymentPurchaseState.amountPaid, "amoundPaid should increase")

        // Second installment payment
        val secondPaymentPurchaseState = payInstallmentAndGetState(
            linearId = initiatedPurchaseState.linearId,
            amount = installment,
            buyer = buyerNode
        )
        assertEquals(installment * 2, secondPaymentPurchaseState.amountPaid, "amoundPaid should increase")
    }

    @Test
    fun `Purchase payment should be recorded by both parties`() {
        val installment = 10.POUNDS
        val initiateTransaction = initiatePurchase(buyerNode, sellerNode, 100.POUNDS, 1)
        network.waitQuiescent()
        val initiatedPurchaseState = initiateTransaction.tx.outputStates.single() as PurchaseState
        payInstallment(
            linearId = initiatedPurchaseState.linearId,
            amount = 10.POUNDS,
            buyer = buyerNode
        )
        network.waitQuiescent()

        for (node in listOf(buyerNode, sellerNode)) {
            node.transaction {
                val purchases = node.services.vaultService.queryBy<PurchaseState>().states

                assertEquals(purchases.size, 1, "One state should be recorded")

                val purchase = purchases.single().state.data

                assertEquals(installment, purchase.amountPaid, "Amount paid should be recorded")
            }
        }
    }

    @Test
    fun `Installment can only be payed by the buyer`() {
        val initiateTransaction = initiatePurchase(buyerNode, sellerNode, 100.POUNDS, 1)
        network.waitQuiescent()
        val initiatedPurchaseState = initiateTransaction.tx.outputStates.single() as PurchaseState

        assertFailsWith<FlowException> {
            payInstallment(initiatedPurchaseState.linearId, 10.POUNDS, sellerNode)
        }
    }

    private fun payInstallmentAndGetState(
        linearId: UniqueIdentifier,
        amount: Amount<Currency>,
        buyer: StartedMockNode
    ): PurchaseState {
        val payInstallmentTransaction = payInstallment(
            linearId = linearId,
            amount = amount,
            buyer = buyer
        )
        network.waitQuiescent()

        return payInstallmentTransaction.tx.outputStates.single() as PurchaseState
    }

    private fun payInstallment(
        linearId: UniqueIdentifier,
        amount: Amount<Currency>,
        buyer: StartedMockNode
    ): SignedTransaction {
        val flow = PurchasePayInstallment.Initiator(linearId, amount)
        return buyer.startFlow(flow).getOrThrow()
    }
}

package com.storda.flows

import co.paralleluniverse.fibers.Suspendable
import com.storda.PurchaseContract
import com.storda.PurchaseState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.POUNDS
import java.util.*

class PurchaseInitiateFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
        private val seller: Party,
        private val price: Amount<Currency>,
        private val itemId: Int
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val outputState = PurchaseState(
                buyer = ourIdentity,
                seller = seller,
                price = price,
                amountPaid = 0.POUNDS,
                itemId = itemId
            )
            val initiateCommand = Command(
                PurchaseContract.Commands.Initiate(),
                outputState.participants.map { it.owningKey })

            val transactionBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, PurchaseContract.PROGRAM_ID)
                .addCommand(initiateCommand)

            transactionBuilder.verify(serviceHub)

            val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

            val sessions = (outputState.participants - ourIdentity).map { initiateFlow(it as Party) }.toSet()

            val fullySignedTransaction = subFlow(CollectSignaturesFlow(signedTransaction, sessions))

            return subFlow(FinalityFlow(fullySignedTransaction))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val signTransactionFlow = object : SignTransactionFlow(counterPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Purchase transaction" using (output is PurchaseState)
                }
            }
            subFlow(signTransactionFlow)
        }

    }
}

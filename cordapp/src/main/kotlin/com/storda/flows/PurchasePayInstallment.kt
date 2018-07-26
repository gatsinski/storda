package com.storda.flows

import co.paralleluniverse.fibers.Suspendable
import com.storda.PurchaseContract
import com.storda.PurchaseState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

class PurchasePayInstallment {

    @InitiatingFlow
    class Initiator(
        private val purchaseId: UniqueIdentifier,
        private val amount: Amount<Currency>
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val stateAndRef = getPurchaseByLinearId(purchaseId)
            val inputState = stateAndRef.state.data

            val buyer = serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputState.buyer)

            if (ourIdentity != buyer) {
                throw FlowException("Only the purchase buyer can pay installments")
            }

            val amountPaid = inputState.amountPaid + amount

            val outputState = inputState.copy(amountPaid = amountPaid)
            val signerKeys = inputState.participants.map { it.owningKey }
            val command = Command(
                PurchaseContract.Commands.PayInstallment(),
                signerKeys
            )

            val builder = TransactionBuilder(notary = notary)
                .addInputState(stateAndRef)
                .addOutputState(outputState, PurchaseContract.PROGRAM_ID)
                .addCommand(command)

            builder.verify(serviceHub)
            val partiallySignedTransaction = serviceHub.signInitialTransaction(builder)
            val sessions = (outputState.participants - ourIdentity).map { initiateFlow(it) }.toSet()
            val signedTransaction = subFlow(CollectSignaturesFlow(partiallySignedTransaction, sessions))

            return subFlow(FinalityFlow(signedTransaction))
        }

        @Suspendable
        private fun getPurchaseByLinearId(linearId: UniqueIdentifier): StateAndRef<PurchaseState> {
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                linearId = listOf(linearId),
                status = Vault.StateStatus.UNCONSUMED
            )
            return serviceHub.vaultService.queryBy<PurchaseState>(queryCriteria).states.singleOrNull()
                    ?: throw FlowException("Purchase with id $linearId not found.")
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(private val counterPartySession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val signedTransactionFlow = object : SignTransactionFlow(counterPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val purchaseState = stx.tx.outputStates.single()
                    "The output state must be a PurchaseState" using (purchaseState is PurchaseState)
                }
            }

            subFlow(signedTransactionFlow)
        }
    }
}

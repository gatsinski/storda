package com.storda.flows

import co.paralleluniverse.fibers.Suspendable
import com.storda.PurchaseContract
import com.storda.PurchaseState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class PurchaseComplete {

    @InitiatingFlow
    class Initiator(private val purchaseId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // Somehint to do now or never
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val stateAndRef = getPurchaseByLinearId(purchaseId)
            val inputState = stateAndRef.state.data

            val buyerIdentity = serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputState.buyer)

            if (ourIdentity != buyerIdentity) {
                throw FlowException("Purchase can only be completed by the buyer")
            }

            val command = Command(
                PurchaseContract.Commands.Complete(),
                inputState.participants.map { it.owningKey }
            )

            val builder = TransactionBuilder(notary = notary)
                .addInputState(stateAndRef)
                .addCommand(command)

            builder.verify(serviceHub)
            val partiallySignedTransaction = serviceHub.signInitialTransaction(builder)
            val sessions = (inputState.participants - ourIdentity).map { initiateFlow(it) }.toSet()
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
    class Responder(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
//            val signTransactionFlow = object : SignTransactionFlow(counterPartySession) {
//
//            }
//
//            subFlow(signTransactionFlow)
            TODO()
        }
    }
}

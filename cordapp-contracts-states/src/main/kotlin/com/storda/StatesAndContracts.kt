package com.storda

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.util.*

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction

class PurchaseContract : Contract {
    companion object {
        const val PROGRAM_ID: ContractClassName = "com.storda.PurchaseContract"
    }

    interface Commands : CommandData {
        class Initiate : TypeOnlyCommandData(), Commands
        class PayInstallment : TypeOnlyCommandData(), Commands
        class Complete : TypeOnlyCommandData(), Commands
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Initiate -> requireThat {
                "No input states should be consumed when initating a purchase" using (tx.inputs.isEmpty())
                "Only one output should be produced when initiating a purchase" using (tx.outputs.size == 1)
                val outputState = tx.outputStates.single() as PurchaseState
                "Price should be greater than zero when initiating a purchase" using (outputState.price.quantity > 0)
                "The paid amount should be zero when initiating a purchase" using (outputState.amountPaid.quantity == 0L)
                "Buyer and seller should be different identities when initiating a purchase" using (outputState.buyer != outputState.seller)
            }

            is Commands.PayInstallment -> requireThat {

            }

            is Commands.Complete -> requireThat {

            }

            else -> throw IllegalArgumentException("Invalid command")
        }
    }

}

// *********
// * State *
// *********
data class PurchaseState(
        val buyer: Party,
        val seller: Party,
        val price: Amount<Currency>,
        val amountPaid: Amount<Currency>,
        val itemId: Int
) : ContractState {
    override val participants: List<AbstractParty> get() = listOf()
}

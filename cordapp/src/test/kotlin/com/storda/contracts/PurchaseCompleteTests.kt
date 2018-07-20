package com.storda.contracts

import com.storda.PurchaseContract
import com.storda.PurchaseState
import net.corda.core.identity.CordaX500Name
import net.corda.finance.POUNDS
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class PurchaseCompleteTests {
    val ledgerServices = MockServices(listOf("com.storda"))
    val buyer = TestIdentity(CordaX500Name("Buyer", "Europe", "BG"))
    val seller = TestIdentity(CordaX500Name("Buyer", "Europe", "BG"))
    val participants = listOf(buyer.publicKey, seller.publicKey)
    val purchase = PurchaseState(buyer.party, seller.party, 10.POUNDS, 10.POUNDS, 100)

    @Test
    fun `Purchase should be completed successfully`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Complete())
                input(PurchaseContract.PROGRAM_ID, purchase)
                verifies()
            }
        }
    }

    @Test
    fun `Only one input state should be consumed when completing a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Complete())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                failsWith(
                    "Only one input state should be consumed when completing a purchase")
            }
        }

    }

    @Test
    fun `No ouput states should be produced when completing a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Complete())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                failsWith("No output states should be produced when completing a purchase")
            }
        }
    }

    @Test
    fun `Paid amount should be equal to price when completing a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Complete())
                input(PurchaseContract.PROGRAM_ID, purchase.copy(price = purchase.price.plus(10.POUNDS)))
                failsWith(
                    "Paid amount should be equal to price before completing a purchase")


            }
        }
    }

    @Test
    fun `Both buyer and seller should sign the transaction when completing a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(buyer.publicKey), PurchaseContract.Commands.Complete())
                input(PurchaseContract.PROGRAM_ID, purchase)
                failsWith(
                    "Both buyer and seller should sign the transaction when completing a purchase")
            }
        }
    }

}
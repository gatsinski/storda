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

class PurchaseInitiateTests {
    val ledgerServices = MockServices(listOf("com.storda"))
    private val buyer = TestIdentity(CordaX500Name("Buyer", "Europe", "BG"))
    private val seller = TestIdentity(CordaX500Name("Seller", "Europe", "BG"))
    private val participants = listOf(buyer.publicKey, seller.publicKey)

    private val purchase = PurchaseState(buyer.party, seller.party, 10.POUNDS, 0.POUNDS, 100)

    @Test
    fun `Purchase should be initiated successfully`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                output(PurchaseContract.PROGRAM_ID, purchase)
                verifies()
            }
        }
    }

    @Test
    fun `No input states should be consumed when initating a purchase`() {
        ledgerServices.ledger() {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                failsWith("No input states should be consumed when initating a purchase")
            }
        }
    }

    @Test
    fun `Only one output should be produced when initiating a purchase`() {
        ledgerServices.ledger() {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                failsWith("Only one output should be produced when initiating a purchase")
            }
        }
    }

    @Test
    fun `amountPaid should be zero when initiating a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                val invalidPurchase = purchase.copy(amountPaid = 10.POUNDS)
                output(PurchaseContract.PROGRAM_ID, invalidPurchase)
                failsWith("The paid amount should be zero when initiating a purchase")
            }
        }
    }

    @Test
    fun `Buyer and seller should be different identities when initiating a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                val invalidPurchase = purchase.copy(seller = buyer.party)
                output(PurchaseContract.PROGRAM_ID, invalidPurchase)
                failsWith("Buyer and seller should be different identities when initiating a purchase")
            }
        }
    }

    @Test
    fun `Price should be greater than zero when initianing a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                val invalidPurchase = purchase.copy(price = 0.POUNDS)
                output(PurchaseContract.PROGRAM_ID, invalidPurchase)
                failsWith("Price should be greater than zero when initiating a purchase")
            }
        }
    }


    @Test
    fun `Both buyer and seller should sign the transaction when initiating a purchase`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.Initiate())
                val wrongSeller = TestIdentity(CordaX500Name("Wrong Seller", "Europe", "BG"))
                val invalidPurchase = purchase.copy(seller = wrongSeller.party)
                output(PurchaseContract.PROGRAM_ID, invalidPurchase)
                failsWith("Both buyer and seller should sign the transaction when initiating a purchase")
            }
        }
    }
}
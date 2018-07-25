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

class PurchasePayInstallmentTests {
    val ledgerServices = MockServices(listOf("com.storda"))
    private val buyer = TestIdentity(CordaX500Name("Buyer", "Europe", "BG"))
    private val seller = TestIdentity(CordaX500Name("Seller", "Europe", "BG"))
    private val participants = listOf(buyer.publicKey, seller.publicKey)

    private val oldPurchase = PurchaseState(buyer.party, seller.party, 10.POUNDS, 0.POUNDS, 100)
    private val newPurchase = oldPurchase.copy(amountPaid = 5.POUNDS)

    @Test
    fun `Installment should be paid successfully`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, oldPurchase)
                output(PurchaseContract.PROGRAM_ID, newPurchase)
                verifies()
            }
        }
    }

    @Test
    fun `Only one input state should be consumed when paying an installment`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                failsWith(
                    "Only one input state should be consumed when paying an installment"
                )
            }
        }
    }

    @Test
    fun `Only one output state should be produced when paying an installment`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, DummyState())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                output(PurchaseContract.PROGRAM_ID, DummyState())
                failsWith(
                    "Only one output should be produced when paying an installment"
                )
            }
        }
    }

    @Test
    fun `Only the amount paid should change when paying an installment`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, oldPurchase)
                output(PurchaseContract.PROGRAM_ID, newPurchase.copy(price = 20.POUNDS))
                failsWith(
                    "Only the amount paid should change when paying an installment"
                )
            }
        }
    }

    @Test
    fun `Amount paid should not be greater than the price when paying an installment`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, oldPurchase)
                output(PurchaseContract.PROGRAM_ID, newPurchase.copy(amountPaid = oldPurchase.price.plus(10.POUNDS)))
                failsWith(
                    "Amount paid should not be greater than the price when paying an installment"
                )
            }
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, oldPurchase)
                output(PurchaseContract.PROGRAM_ID, newPurchase.copy(amountPaid = oldPurchase.price))
                verifies()
            }
        }
    }

    @Test
    fun `Paid amount should increase when paying an installment`() {
        ledgerServices.ledger {
            transaction {
                command(participants, PurchaseContract.Commands.PayInstallment())
                input(PurchaseContract.PROGRAM_ID, newPurchase)  // 5.POUNDS
                output(PurchaseContract.PROGRAM_ID, oldPurchase)  // 0.POUNDS
                failsWith("Paid amound should increase when paying an installment")
            }
        }
    }

    @Test
    fun `Both buyer and seller should sign the transaction when paying an installment`() {
        ledgerServices.ledger {
            transaction {
                command(listOf(buyer.publicKey), PurchaseContract.Commands.PayInstallment())
                TestIdentity(CordaX500Name("Wrong Seller", "Europe", "BG"))
                input(PurchaseContract.PROGRAM_ID, oldPurchase)
                output(PurchaseContract.PROGRAM_ID, newPurchase)
                failsWith(
                    "Both buyer and seller should sign the transaction when paying an installment"
                )
            }
        }
    }
}

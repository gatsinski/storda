package com.storda

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.concurrent.Future
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder
import kotlin.test.assertEquals

class DriverBasedTest {
    private val buyer = TestIdentity(CordaX500Name("BankA", "", "GB"))
    private val seller = TestIdentity(CordaX500Name("BankB", "", "US"))

    @Test
    fun `node test`() = withDriver {
        // Start a pair of nodes and wait for them both to be ready.
        val (partyAHandle, partyBHandle) = startNodes(buyer, seller)

        // From each node, make an RPC call to retrieve another node's name from the network map, to verify that the
        // nodes have started and can communicate.

        // This is a very basic test: in practice tests would be starting flows, and verifying the states in the vault
        // and other important metrics to ensure that your CorDapp is working as intended.
        assertEquals(seller.name, partyAHandle.resolveName(seller.name))
        assertEquals(buyer.name, partyBHandle.resolveName(buyer.name))
    }

    @Test
    fun `get purchase list`() = withDriver {
        // This test starts each node's webserver and makes an HTTP call to retrieve the body of a GET endpoint on
        // the node's webserver, to verify that the nodes' webservers have started and have loaded the API.
        startWebServers(buyer, seller).forEach {
            val request = Request.Builder()
                .url("http://${it.listenAddress}/api/storda/purchases")
                .build()
            val response = OkHttpClient().newCall(request).execute()

            assertEquals("[ ]", response.body().string())
        }
    }


    @Test
    fun `issue purchase test`() = withDriver {
        // This test starts each node's webserver and makes an HTTP call to retrieve the body of a GET endpoint on
        // the node's webserver, to verify that the nodes' webservers have started and have loaded the API.

        val (bankAHandle, _) = listOf(buyer, seller).map {
            startNode(providedName = it.name)
        }.transpose().getOrThrow()

        val webserverHandle = startWebserver(bankAHandle).getOrThrow()
        val listenAddress = webserverHandle.listenAddress
        val uri = UriBuilder.fromUri("http://${listenAddress}/api/storda/purchases")
            .queryParam("seller", seller.party.name)
            .queryParam("priceAmount", 10)
            .queryParam("priceCurrency", "GBP")
            .queryParam("itemId", 1)
            .build()

        val emptyBody = RequestBody.create(MediaType.parse("applicatoin/json"), "")

        val request = Request.Builder()
            .url(uri.toString())
            .post(emptyBody)
            .build()
        val response = OkHttpClient().newCall(request).execute()

        assertEquals(Response.Status.ACCEPTED.statusCode, response.code())

        val states = bankAHandle.rpc.vaultQueryBy<PurchaseState>().states
        assertEquals(states.size, 1)

        val expectedResponse = JSONObject()
            .put("seller", seller.party.name.toString())
            .put("price", "10.00 GBP")
            .put("itemId", 1)

        JSONAssert.assertEquals(expectedResponse, JSONObject(response.body().string()), false)
    }

    @Test
    fun `wrong input issue purchase test`() = withDriver {
        val (bankAHandle, _) = listOf(buyer, seller).map {
            startNode(providedName = it.name)
        }.transpose().getOrThrow()

        val webserverHandle = startWebserver(bankAHandle).getOrThrow()
        val listenAddress = webserverHandle.listenAddress
        val uri = UriBuilder.fromUri("http://${listenAddress}/api/storda/purchases")
            .queryParam("seller", "invalid seller")
            .queryParam("priceAmount", 10)
            .queryParam("priceCurrency", "GBP")
            .queryParam("itemId", 1)
            .build()

        val emptyBody = RequestBody.create(MediaType.parse("applicatoin/json"), "")

        val request = Request.Builder()
            .url(uri.toString())
            .post(emptyBody)
            .build()
        val response = OkHttpClient().newCall(request).execute()

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.code())
    }

    // region Utility functions

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(isDebug = true, startNodesInProcess = true)
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name) }
        .waitForAll()

    // Starts multiple webservers simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startWebServers(vararg identities: TestIdentity) = startNodes(*identities)
        .map { startWebserver(it) }
        .waitForAll()

    // endregion
}

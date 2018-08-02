package com.storda

import co.paralleluniverse.fibers.Suspendable
import com.storda.flows.PurchaseInitiateFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.serialization.SerializationWhitelist
import net.corda.finance.POUNDS
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.*
import java.util.function.Function
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("storda")
class StordaApi(val services: CordaRPCOps) {
    // Accessible at /api/storda/getPurchaseStates.
    @GET
    @Path("purchases")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPurchaseStates(): List<StateAndRef<PurchaseState>> {
        return services.vaultQueryBy<PurchaseState>().states
    }

    @POST
    @Path("purchases")
    @Produces(MediaType.APPLICATION_JSON)
    fun initiatePurchase(
        @QueryParam(value = "seller") seller: String,
        @QueryParam(value = "priceAmount") priceAmount: Int,
        @QueryParam(value = "priceCurrency") priceCurrency: String,
        @QueryParam(value = "itemId") itemId: Int
    ): Response {

//        val me = services.nodeInfo().legalIdentities.first()
        val counterParty = services.wellKnownPartyFromX500Name(CordaX500Name.parse(seller)) ?:
                throw WebApplicationException("Invalid seller", Response.Status.BAD_REQUEST)
        val amount = Amount(
            priceAmount.toLong() * 100,
            Currency.getInstance(priceCurrency)
        )

        val result = services.startFlowDynamic(PurchaseInitiateFlow.Initiator::class.java, counterParty, amount, itemId)
            .returnValue
            .get()

        val purchaseState = result.tx.outputStates.single() as PurchaseState

        val map = mapOf(
            "seller" to purchaseState.seller,
            "price" to purchaseState.price,
            "itemId" to purchaseState.itemId
        )

        return Response
            .accepted(map)
            .build()
    }
}

// ***********
// * Plugins *
// ***********
class WebPlugin : WebServerPluginRegistry {
    // A list of lambdas that create objects exposing web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::StordaApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This storda's web frontend is accessible at /web/storda.
    override val staticServeDirs: Map<String, String> = mapOf(
        // This will serve the storda directory in resources to /web/storda
        "storda" to javaClass.classLoader.getResource("storda").toExternalForm()
    )
}

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)

package app.trecos.help

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A tip on offer.
 *
 * @property id the Play product id.
 * @property price the price as Play formats it.
 */
data class Tip(val id: String, val price: String)

/** How a tip ended. */
enum class TipOutcome { Thanked, Cancelled, Failed }

/** The tip jar (spec "Tips"): three repeatable consumables that unlock nothing; tests use a fake. */
interface TipJar {
    /**
     * @param context a context.
     * @return the tips, or `null` when Google Play isn't available.
     */
    suspend fun tips(context: Context): List<Tip>?

    /**
     * Buys a tip through Google Play.
     *
     * @param activity the current activity.
     * @param id the tip's product id.
     * @return how it ended.
     */
    suspend fun buy(activity: Activity, id: String): TipOutcome
}

/** Play Billing with consumable products `tip_small`, `tip_medium` and `tip_large` (design D18). */
class PlayTipJar : TipJar {
    private var client: BillingClient? = null
    private var details: Map<String, ProductDetails> = emptyMap()
    private var pending: ((TipOutcome) -> Unit)? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val done = pending ?: return@PurchasesUpdatedListener
        pending = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach(::consume)
                done(TipOutcome.Thanked)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> done(TipOutcome.Cancelled)
            else -> done(TipOutcome.Failed)
        }
    }

    override suspend fun tips(context: Context): List<Tip>? {
        val billing = connect(context) ?: return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            IDS.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build() },
        ).build()
        val found = suspendCancellableCoroutine<List<ProductDetails>?> { continuation ->
            billing.queryProductDetailsAsync(params) { result, list ->
                continuation.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) list.productDetailsList else null)
            }
        } ?: return null
        details = found.associateBy { it.productId }
        return IDS.mapNotNull { id -> details[id]?.oneTimePurchaseOfferDetails?.let { Tip(id, it.formattedPrice) } }.ifEmpty { null }
    }

    override suspend fun buy(activity: Activity, id: String): TipOutcome {
        val billing = client ?: return TipOutcome.Failed
        val product = details[id] ?: return TipOutcome.Failed
        return suspendCancellableCoroutine { continuation ->
            pending = { continuation.resume(it) }
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
                .build()
            val started = billing.launchBillingFlow(activity, flow)
            if (started.responseCode != BillingClient.BillingResponseCode.OK) {
                pending = null
                continuation.resume(TipOutcome.Failed)
            }
        }
    }

    /** Consumes a tip so it can be bought again. */
    private fun consume(purchase: Purchase) {
        client?.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { _, _ -> }
    }

    /** @return a connected client, or `null` when Google Play isn't there. */
    private suspend fun connect(context: Context): BillingClient? {
        client?.takeIf { it.isReady }?.let { return it }
        val billing = BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        val ok = suspendCancellableCoroutine { continuation ->
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
        return if (ok) billing.also { client = it } else null
    }

    private companion object {
        val IDS = listOf("tip_small", "tip_medium", "tip_large")
    }
}

package com.gallery.sync.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.gallery.sync.domain.billing.BillingRepository
import com.gallery.sync.domain.billing.BillingRepository.Companion.PRO_UNLOCK
import com.gallery.sync.domain.billing.PurchaseOutcome
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [BillingRepository] backed by Play Billing Library directly.
 *
 * No backend server, same as the rest of this app — see TASK-026 on why GallerySync has none at
 * all. `Purchase.PurchaseState.PURCHASED` alone is Play's client library reporting what it was
 * told, not proof; [verifySignature] is the local check that stands in for the server-side receipt
 * check a backend would normally do. Every purchase this class treats as owned has passed it —
 * see [ownedProUnlockPurchase] and the `PurchaseReceived` branch of [purchase].
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : BillingRepository, PurchasesUpdatedListener {

    /** What [onPurchasesUpdated] handed back, for [purchase] to await and finish processing. */
    private sealed interface FlowOutcome {
        data class PurchaseReceived(val purchase: Purchase) : FlowOutcome
        data object Cancelled : FlowOutcome
        data object AlreadyOwned : FlowOutcome
        data class Failed(val debugMessage: String) : FlowOutcome
    }

    /**
     * Set by [purchase] just before calling `launchBillingFlow`, resolved by [onPurchasesUpdated]
     * when Play calls back. There is only ever one purchase flow in flight at a time — Play's own
     * UI is modal — so a single field is enough, unlike [com.gallery.sync.data.remote.auth
     * .GoogleSignInResultBridge]'s equivalent, which has to survive a separate Activity launch.
     */
    @Volatile
    private var pendingFlow: CompletableDeferred<FlowOutcome>? = null

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    private val connectLock = Mutex()

    @Volatile
    private var connected = false

    override suspend fun isPurchased(): Boolean {
        val purchase = ownedProUnlockPurchase() ?: return false
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        if (!purchase.isAcknowledged) acknowledge(purchase)
        return true
    }

    override suspend fun purchase(activity: Activity): PurchaseOutcome {
        if (!ensureConnected()) return PurchaseOutcome.Failed("could not connect to Play")

        // Ask first: launching Play's purchase sheet for something already owned is a wasted round
        // trip through their UI, and Play would answer ITEM_ALREADY_OWNED anyway.
        ownedProUnlockPurchase()?.let { owned ->
            if (owned.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!owned.isAcknowledged) acknowledge(owned)
                return PurchaseOutcome.AlreadyOwned
            }
        }

        val productDetails = queryProUnlockDetails()
            ?: return PurchaseOutcome.Failed("pro_unlock is not available from Play right now")
        val offerToken = productDetails.oneTimePurchaseOfferDetails?.offerToken
            ?: return PurchaseOutcome.Failed("pro_unlock has no active purchase option")

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        val deferred = CompletableDeferred<FlowOutcome>()
        pendingFlow = deferred

        val launched = client.launchBillingFlow(activity, flowParams)
        if (launched.responseCode != BillingResponseCode.OK) {
            pendingFlow = null
            return PurchaseOutcome.Failed("launchBillingFlow: ${launched.debugMessage}")
        }

        return when (val outcome = deferred.await()) {
            is FlowOutcome.PurchaseReceived -> {
                if (!verifySignature(outcome.purchase)) {
                    // Fails closed: an unverifiable purchase grants nothing, the same as one that
                    // never happened. This is the one place a fake "purchased" result could try to
                    // reach the app, since it arrives straight from onPurchasesUpdated rather than
                    // from ownedProUnlockPurchase's own filtered query.
                    PurchaseOutcome.Failed("purchase could not be verified")
                } else {
                    if (!outcome.purchase.isAcknowledged) acknowledge(outcome.purchase)
                    Logger.i(TAG, "pro_unlock purchased")
                    PurchaseOutcome.Success
                }
            }

            FlowOutcome.Cancelled -> PurchaseOutcome.Cancelled
            FlowOutcome.AlreadyOwned -> PurchaseOutcome.AlreadyOwned
            is FlowOutcome.Failed -> PurchaseOutcome.Failed(outcome.debugMessage)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val deferred = pendingFlow
        pendingFlow = null

        val outcome = when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull { PRO_UNLOCK in it.products }
                if (purchase != null) {
                    FlowOutcome.PurchaseReceived(purchase)
                } else {
                    FlowOutcome.Failed("purchase completed but pro_unlock was not in the result")
                }
            }

            BillingResponseCode.USER_CANCELED -> FlowOutcome.Cancelled
            BillingResponseCode.ITEM_ALREADY_OWNED -> FlowOutcome.AlreadyOwned
            else -> FlowOutcome.Failed(billingResult.debugMessage)
        }

        deferred?.let { if (!it.isCompleted) it.complete(outcome) }
    }

    /**
     * The signed-in account's `pro_unlock` purchase, or null when there isn't one or it fails
     * [verifySignature]. Every caller of this method gets the verification for free — an
     * unverifiable purchase reads as "does not exist" everywhere it's used.
     */
    private suspend fun ownedProUnlockPurchase(): Purchase? {
        if (!ensureConnected()) return null
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        return result.purchasesList.firstOrNull { PRO_UNLOCK in it.products && verifySignature(it) }
    }

    /**
     * Verifies [purchase]'s signature against the Play Console Licensing public key. The actual
     * cryptography is [verifyPurchaseSignature] — a pure function with real unit test coverage;
     * this is just the seam that pulls the two strings it needs out of a [Purchase].
     */
    private fun verifySignature(purchase: Purchase): Boolean {
        val verified = verifyPurchaseSignature(purchase.originalJson, purchase.signature, LICENSING_PUBLIC_KEY)
        if (!verified) Logger.e(TAG, "pro_unlock purchase signature did not verify")
        return verified
    }

    private suspend fun queryProUnlockDetails(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_UNLOCK)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()
        return client.queryProductDetails(params).productDetailsList?.firstOrNull()
    }

    /**
     * Never allowed to throw or stop a caller: an unacknowledged purchase is retried the next time
     * [isPurchased] runs, and Play's 3-day acknowledgement window leaves plenty of slack for that.
     */
    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = client.acknowledgePurchase(params)
        if (result.responseCode == BillingResponseCode.OK) {
            Logger.i(TAG, "acknowledged pro_unlock")
        } else {
            Logger.w(TAG, "could not acknowledge pro_unlock: ${result.debugMessage}")
        }
    }

    /** Connects once, lazily. Cheap to call on every entry point — a live connection is a no-op. */
    private suspend fun ensureConnected(): Boolean {
        if (connected) return true
        return connectLock.withLock {
            if (connected) return@withLock true
            val result = suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) continuation.resume(billingResult)
                    }

                    override fun onBillingServiceDisconnected() {
                        connected = false
                    }
                })
            }
            connected = result.responseCode == BillingResponseCode.OK
            if (!connected) {
                Logger.w(TAG, "could not connect to Play Billing: ${result.debugMessage}")
            }
            connected
        }
    }

    private companion object {
        const val TAG = "Billing"

        /**
         * GallerySync's Play Console Licensing public key (Monetization setup → Licensing). A
         * public key, not a secret — it verifies signatures Play signs with the matching private
         * key it holds, and shipping it inside the APK is exactly how it's meant to be used.
         * Leaking it grants nothing: the worst it enables is helping someone patch a cracked APK to
         * skip verification, which they could do anyway by removing the check itself. Ian, 23 Sept
         * 2026.
         */
        const val LICENSING_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5JCcXksdQJfdpVq92v52424+oTnTHygpkNCxlU2jxdBIa0i" +
                "VRMQasp6gcWLlYtEGTjV/1XoiYiYX9vaPpnJZM6fL/Bbag0BCZNew1yIt3hrl1LMjGp24NNg9thWNyv+66rYluZbb" +
                "KSkqFSaAxyTZAM86zloeBziWLJ/qrD4eBZ4GGsdycfOY1TNyoVsd9tH2+sjaTonr2zFArIhCRfbR+MmH3WO4vYDbg" +
                "tKlnDhJkcOhQrl/IFbcWwEyJdG5Ac3N5xwQJwED4Bhug9ZEZqZxZfUIdB7aw5VADl+foXlFljQ8tUrPjriO3eMVg6" +
                "ieljfxcf+yU1QwYCwBup24fwuPqwIDAQAB"
    }
}

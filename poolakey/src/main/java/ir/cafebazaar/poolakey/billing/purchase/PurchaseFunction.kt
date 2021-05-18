package ir.cafebazaar.poolakey.billing.purchase

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import com.android.vending.billing.IInAppBillingService
import ir.cafebazaar.poolakey.PurchaseType
import ir.cafebazaar.poolakey.billing.BillingFunction
import ir.cafebazaar.poolakey.callback.PurchaseIntentCallback
import ir.cafebazaar.poolakey.constant.BazaarIntent
import ir.cafebazaar.poolakey.constant.Billing
import ir.cafebazaar.poolakey.exception.BazaarNotSupportedException
import ir.cafebazaar.poolakey.exception.ResultNotOkayException
import ir.cafebazaar.poolakey.request.PurchaseRequest
import ir.cafebazaar.poolakey.request.purchaseExtraData
import ir.cafebazaar.poolakey.takeIf

internal class PurchaseFunction(
    private val context: Context
) : BillingFunction<PurchaseFunctionRequest> {

    override fun function(
        billingService: IInAppBillingService,
        request: PurchaseFunctionRequest
    ): Unit = with(request) {
        try {
            val purchaseConfigBundle = billingService.getPurchaseConfig(
                Billing.IN_APP_BILLING_VERSION
            )

            val intentResponseIsNullError = {
                PurchaseIntentCallback().apply(callback)
                    .failedToBeginFlow
                    .invoke(IllegalStateException("Couldn't receive buy intent from Bazaar"))
            }

            when {
                doesClientSupportIntentV3(purchaseConfigBundle) -> {
                    fireBuyIntentV3(billingService, intentResponseIsNullError)
                }
                doesClientSupportIntentV2(purchaseConfigBundle) -> {
                    fireBuyIntentV2(billingService, intentResponseIsNullError)
                }
                else -> {
                    fireBuyIntent(billingService, intentResponseIsNullError)
                }
            }
        } catch (e: RemoteException) {
            PurchaseIntentCallback().apply(callback).failedToBeginFlow.invoke(e)
        }
    }

    private fun PurchaseFunctionRequest.fireBuyIntentV3(
        billingService: IInAppBillingService,
        intentResponseIsNullError: () -> Unit
    ) {

        if (purchaseRequest.discount.isNullOrEmpty().not()) {
            PurchaseIntentCallback().apply(callback).failedToBeginFlow.invoke(
                BazaarNotSupportedException()
            )
            return
        }

        getBuyIntentV3FromBillingService(
            billingService,
            purchaseRequest,
            purchaseRequest.purchaseExtraData(),
            callback
        )?.takeIf(
            thisIsTrue = { bundle ->
                bundle.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY) != null
            }, andIfNot = intentResponseIsNullError
        )?.getParcelable<Intent>(INTENT_RESPONSE_BUY)?.also { purchaseIntent ->
            fireIntentWithIntent.invoke(purchaseIntent)
        }
    }

    private fun PurchaseFunctionRequest.fireBuyIntentV2(
        billingService: IInAppBillingService,
        intentResponseIsNullError: () -> Unit
    ) {
        getBuyIntentV2FromBillingService(
            billingService,
            purchaseRequest,
            purchaseType,
            callback
        )?.takeIf(
            thisIsTrue = { bundle ->
                bundle.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY) != null
            }, andIfNot = intentResponseIsNullError
        )?.getParcelable<Intent>(INTENT_RESPONSE_BUY)?.also { purchaseIntent ->
            fireIntentWithIntent.invoke(purchaseIntent)
        }
    }

    private fun PurchaseFunctionRequest.fireBuyIntent(
        billingService: IInAppBillingService,
        intentResponseIsNullError: () -> Unit
    ) {
        getBuyIntentFromBillingService(
            billingService,
            purchaseRequest,
            purchaseType,
            callback
        )?.takeIf(
            thisIsTrue = { bundle ->
                bundle.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY) != null
            }, andIfNot = intentResponseIsNullError
        )?.getParcelable<PendingIntent>(INTENT_RESPONSE_BUY)?.also { purchaseIntent ->
            fireIntentWithIntentSender.invoke(purchaseIntent.intentSender)
        }
    }

    private inline fun getBuyIntentFromBillingService(
        billingService: IInAppBillingService,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseIntentCallback.() -> Unit
    ) = billingService.getBuyIntent(
        Billing.IN_APP_BILLING_VERSION,
        context.packageName,
        purchaseRequest.productId,
        purchaseType.type,
        purchaseRequest.payload
    )?.takeBundleIfResponseIsOk(callback)

    private inline fun getBuyIntentV2FromBillingService(
        billingService: IInAppBillingService,
        purchaseRequest: PurchaseRequest,
        purchaseType: PurchaseType,
        callback: PurchaseIntentCallback.() -> Unit
    ) = billingService.getBuyIntentV2(
        Billing.IN_APP_BILLING_VERSION,
        context.packageName,
        purchaseRequest.productId,
        purchaseType.type,
        purchaseRequest.payload
    )?.takeBundleIfResponseIsOk(callback)

    private inline fun getBuyIntentV3FromBillingService(
        billingService: IInAppBillingService,
        purchaseRequest: PurchaseRequest,
        extraData: Bundle,
        callback: PurchaseIntentCallback.() -> Unit
    ) = billingService.getBuyIntentV3(
        Billing.IN_APP_BILLING_VERSION,
        context.packageName,
        purchaseRequest.productId,
        purchaseRequest.payload,
        extraData
    )?.takeBundleIfResponseIsOk(callback)

    private inline fun Bundle.takeBundleIfResponseIsOk(
        callback: PurchaseIntentCallback.() -> Unit
    ): Bundle? = takeIf(
        thisIsTrue = { bundle ->
            bundle.get(BazaarIntent.RESPONSE_CODE) == BazaarIntent.RESPONSE_RESULT_OK
        }, andIfNot = {
            PurchaseIntentCallback().apply(callback)
                .failedToBeginFlow
                .invoke(ResultNotOkayException())
        }
    )

    private fun doesClientSupportIntentV2(purchaseConfigBundle: Bundle?): Boolean {
        return purchaseConfigBundle?.getBoolean(INTENT_V2_SUPPORT) ?: false
    }

    private fun doesClientSupportIntentV3(purchaseConfigBundle: Bundle?): Boolean {
        return purchaseConfigBundle?.getBoolean(INTENT_V3_SUPPORT) ?: false
    }

    companion object {
        private const val INTENT_RESPONSE_BUY = "BUY_INTENT"
        private const val INTENT_V2_SUPPORT = "INTENT_V2_SUPPORT"
        private const val INTENT_V3_SUPPORT = "INTENT_V3_SUPPORT"
    }

}
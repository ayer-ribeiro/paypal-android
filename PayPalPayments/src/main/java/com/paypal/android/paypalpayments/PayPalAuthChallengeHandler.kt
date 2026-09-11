package com.paypal.android.paypalpayments

import android.content.Intent
import android.net.Uri
import com.paypal.android.corepayments.BrowserSwitchRequestCodes
import com.paypal.android.corepayments.CaptureDeepLinkResult
import com.paypal.android.corepayments.DeepLink
import com.paypal.android.corepayments.ReturnToAppStrategy
import com.paypal.android.corepayments.browserswitch.BrowserSwitchLaunchMode
import com.paypal.android.corepayments.browserswitch.BrowserSwitchOptions
import com.paypal.android.corepayments.browserswitch.BrowserSwitchPendingState
import com.paypal.android.corepayments.captureDeepLink
import com.paypal.android.corepayments.model.TokenType
import com.paypal.android.paypalpayments.errors.PayPalError
import org.json.JSONObject

internal class PayPalAuthChallengeHandler {

    companion object {
        private const val METADATA_KEY_ORDER_ID = "order_id"
        private const val METADATA_KEY_SETUP_TOKEN_ID = "setup_token_id"

        private const val URL_PARAM_APPROVAL_SESSION_ID = "approval_session_id"
    }

    fun createAuthChallenge(
        uri: Uri,
        token: String,
        tokenType: TokenType,
        returnToAppStrategy: ReturnToAppStrategy,
        launchMode: BrowserSwitchLaunchMode = BrowserSwitchLaunchMode.AUTH_TAB,
    ): PayPalPresentAuthChallengeResult {
        val metadata = getMetadata(token, tokenType)
        val options = BrowserSwitchOptions(
            targetUri = uri,
            requestCode = getRequestCode(tokenType),
            returnUrlScheme = (returnToAppStrategy as? ReturnToAppStrategy.CustomUrlScheme)?.urlScheme,
            appLinkUrl = (returnToAppStrategy as? ReturnToAppStrategy.AppLink)?.appLinkUrl,
            metadata = metadata,
            launchMode = launchMode,
        )
        val authState = BrowserSwitchPendingState(options).toBase64EncodedJSON()
        val authChallenge = PayPalAuthChallenge(options)
        return PayPalPresentAuthChallengeResult.Success(authChallenge, authState)
    }

    private fun getRequestCode(tokenType: TokenType): Int {
        return when (tokenType) {
            TokenType.ORDER_ID -> BrowserSwitchRequestCodes.PAYPAL_CHECKOUT
            TokenType.VAULT_ID -> BrowserSwitchRequestCodes.PAYPAL_VAULT
            TokenType.BILLING_TOKEN -> BrowserSwitchRequestCodes.PAYPAL_VAULT
        }
    }

    private fun getMetadata(
        token: String,
        tokenType: TokenType
    ) = JSONObject().apply {
        when (tokenType) {
            TokenType.ORDER_ID -> put(METADATA_KEY_ORDER_ID, token)
            TokenType.VAULT_ID -> put(METADATA_KEY_SETUP_TOKEN_ID, token)
            TokenType.BILLING_TOKEN -> put(METADATA_KEY_SETUP_TOKEN_ID, token)
        }
    }

    fun completeCheckoutAuthRequest(
        intent: Intent,
        authState: String
    ): PayPalFinishStartResult {
        val requestCode = BrowserSwitchRequestCodes.PAYPAL_CHECKOUT
        return when (val result = captureDeepLink(requestCode, intent, authState)) {
            is CaptureDeepLinkResult.Success -> parseWebCheckoutSuccessResult(result.deepLink)
            is CaptureDeepLinkResult.Failure ->
                PayPalFinishStartResult.Failure(result.reason, orderId = null)

            is CaptureDeepLinkResult.Ignore -> PayPalFinishStartResult.NoResult
        }
    }

    fun completeCheckoutAuthRequest(
        result: PayPalLaunchResult,
        authState: String,
    ): PayPalFinishStartResult = when (result) {
        is PayPalLaunchResult.Success -> completeCheckoutAuthRequest(
            Intent(Intent.ACTION_VIEW, result.resultUri),
            authState,
        )

        PayPalLaunchResult.Canceled -> PayPalFinishStartResult.Canceled(
            getOriginalOptions(authState)?.metadata?.optString(METADATA_KEY_ORDER_ID)
        )

        is PayPalLaunchResult.Failure -> PayPalFinishStartResult.Failure(
            result.error,
            getOriginalOptions(authState)?.metadata?.optString(METADATA_KEY_ORDER_ID),
        )
    }

    fun completeVaultAuthRequest(
        intent: Intent,
        authState: String
    ): PayPalFinishVaultResult {
        val requestCode = BrowserSwitchRequestCodes.PAYPAL_VAULT
        return when (val result = captureDeepLink(requestCode, intent, authState)) {
            is CaptureDeepLinkResult.Success -> parseVaultSuccessResult(result.deepLink)
            is CaptureDeepLinkResult.Failure ->
                PayPalFinishVaultResult.Failure(result.reason)

            is CaptureDeepLinkResult.Ignore -> PayPalFinishVaultResult.NoResult
        }
    }

    fun completeVaultAuthRequest(
        result: PayPalLaunchResult,
        authState: String,
    ): PayPalFinishVaultResult = when (result) {
        is PayPalLaunchResult.Success -> completeVaultAuthRequest(
            Intent(Intent.ACTION_VIEW, result.resultUri),
            authState,
        )

        PayPalLaunchResult.Canceled -> PayPalFinishVaultResult.Canceled
        is PayPalLaunchResult.Failure -> PayPalFinishVaultResult.Failure(result.error)
    }

    private fun getOriginalOptions(authState: String): BrowserSwitchOptions? =
        BrowserSwitchPendingState.fromBase64(authState)?.originalOptions

    private fun parseWebCheckoutSuccessResult(
        deepLink: DeepLink
    ): PayPalFinishStartResult {
        val metadata = deepLink.originalOptions.metadata
        return if (metadata == null) {
            val unknownError = PayPalError.unknownError
            PayPalFinishStartResult.Failure(unknownError, null)
        } else {
            val orderId = metadata.optString(METADATA_KEY_ORDER_ID)
            val payerId = deepLink.uri.getQueryParameter("PayerID")
            val isCancelUrl = deepLink.uri.path?.contains("cancel") ?: false
            if (isCancelUrl) {
                PayPalFinishStartResult.Canceled(orderId)
            } else {
                when {
                    !orderId.isNullOrBlank() && !payerId.isNullOrBlank() ->
                        PayPalFinishStartResult.Success(orderId, payerId)
                    orderId.isNullOrBlank() ->
                        PayPalFinishStartResult.Failure(
                            PayPalError.malformedResultError,
                            orderId
                        )
                    else -> PayPalFinishStartResult.Canceled(orderId)
                }
            }
        }
    }

    private fun parseVaultSuccessResult(
        deepLink: DeepLink
    ): PayPalFinishVaultResult {
        val requestMetadata = deepLink.originalOptions.metadata
        return if (requestMetadata == null) {
            PayPalFinishVaultResult.Failure(PayPalError.unknownError)
        } else {
            val isCancelUrl = deepLink.uri.path?.contains("cancel") ?: false
            if (isCancelUrl) {
                PayPalFinishVaultResult.Canceled
            } else {
                val approvalSessionId =
                    deepLink.uri.getQueryParameter(URL_PARAM_APPROVAL_SESSION_ID)
                if (approvalSessionId.isNullOrEmpty()) {
                    PayPalFinishVaultResult.Failure(PayPalError.malformedResultError)
                } else {
                    PayPalFinishVaultResult.Success(approvalSessionId)
                }
            }
        }
    }
}

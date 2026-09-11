package com.paypal.android.paypalpayments

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.paypal.android.corepayments.BrowserSwitchRequestCodes.PAYPAL_CHECKOUT
import com.paypal.android.corepayments.BrowserSwitchRequestCodes.PAYPAL_VAULT
import com.paypal.android.corepayments.PayPalSDKError
import com.paypal.android.corepayments.ReturnToAppStrategy
import com.paypal.android.corepayments.browserswitch.BrowserSwitchLaunchMode
import com.paypal.android.corepayments.browserswitch.BrowserSwitchOptions
import com.paypal.android.corepayments.browserswitch.BrowserSwitchPendingState
import com.paypal.android.corepayments.model.TokenType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PayPalAuthChallengeHandlerUnitTest {

    private lateinit var sut: PayPalAuthChallengeHandler

    @Before
    fun beforeEach() {
        sut = PayPalAuthChallengeHandler()
    }

    @Test
    fun `createAuthChallenge() creates checkout Auth Tab challenge`() {
        val result = sut.createAuthChallenge(
            uri = "https://www.sandbox.paypal.com/checkoutnow".toUri(),
            token = "order-123",
            tokenType = TokenType.ORDER_ID,
            returnToAppStrategy = ReturnToAppStrategy.CustomUrlScheme("merchant.app"),
        ) as PayPalPresentAuthChallengeResult.Success

        with(result.authChallenge.browserSwitchOptions) {
            assertEquals("order-123", metadata?.getString("order_id"))
            assertEquals("merchant.app", returnUrlScheme)
            assertEquals("https://www.sandbox.paypal.com/checkoutnow".toUri(), targetUri)
            assertEquals(PAYPAL_CHECKOUT, requestCode)
            assertEquals(BrowserSwitchLaunchMode.AUTH_TAB, launchMode)
        }
        val restoredOptions = BrowserSwitchPendingState.fromBase64(result.authState)?.originalOptions
        assertEquals(result.authChallenge.browserSwitchOptions.targetUri, restoredOptions?.targetUri)
        assertEquals(result.authChallenge.browserSwitchOptions.requestCode, restoredOptions?.requestCode)
        assertEquals(result.authChallenge.browserSwitchOptions.returnUrlScheme, restoredOptions?.returnUrlScheme)
        assertEquals(result.authChallenge.browserSwitchOptions.launchMode, restoredOptions?.launchMode)
    }

    @Test
    fun `createAuthChallenge() creates vault app-switch challenge with App Link`() {
        val result = sut.createAuthChallenge(
            uri = "https://www.paypal.com/app-switch".toUri(),
            token = "setup-token-123",
            tokenType = TokenType.VAULT_ID,
            returnToAppStrategy = ReturnToAppStrategy.AppLink(
                "https://merchant.example/paypal/return"
            ),
            launchMode = BrowserSwitchLaunchMode.CUSTOM_TAB,
        ) as PayPalPresentAuthChallengeResult.Success

        with(result.authChallenge.browserSwitchOptions) {
            assertEquals("setup-token-123", metadata?.getString("setup_token_id"))
            assertEquals(null, returnUrlScheme)
            assertEquals("https://merchant.example/paypal/return", appLinkUrl)
            assertEquals(PAYPAL_VAULT, requestCode)
            assertEquals(BrowserSwitchLaunchMode.CUSTOM_TAB, launchMode)
        }
    }

    @Test
    fun `completeCheckoutAuthRequest() parses successful Intent result`() {
        val authState = checkoutAuthState()
        val intent = Intent().setData(checkoutDeepLink("payer-123"))

        val result = sut.completeCheckoutAuthRequest(intent, authState)
            as PayPalFinishStartResult.Success

        assertEquals("order-123", result.orderId)
        assertEquals("payer-123", result.payerId)
    }

    @Test
    fun `completeCheckoutAuthRequest() parses cancel path from Intent`() {
        val intent = Intent().setData(
            "merchant.app://paypal/checkout/cancel?PayerID=payer-123".toUri()
        )

        val result = sut.completeCheckoutAuthRequest(intent, checkoutAuthState())
            as PayPalFinishStartResult.Canceled

        assertEquals("order-123", result.orderId)
    }

    @Test
    fun `completeCheckoutAuthRequest() maps launch cancellation`() {
        val result = sut.completeCheckoutAuthRequest(
            PayPalLaunchResult.Canceled,
            checkoutAuthState(),
        ) as PayPalFinishStartResult.Canceled

        assertEquals("order-123", result.orderId)
    }

    @Test
    fun `completeCheckoutAuthRequest() parses successful launcher result`() {
        val result = sut.completeCheckoutAuthRequest(
            PayPalLaunchResult.Success(checkoutDeepLink("payer-123")),
            checkoutAuthState(),
        ) as PayPalFinishStartResult.Success

        assertEquals("order-123", result.orderId)
        assertEquals("payer-123", result.payerId)
    }

    @Test
    fun `completeCheckoutAuthRequest() maps launcher failure and order id`() {
        val error = PayPalSDKError(0, "Auth Tab failed")

        val result = sut.completeCheckoutAuthRequest(
            PayPalLaunchResult.Failure(error),
            checkoutAuthState(),
        ) as PayPalFinishStartResult.Failure

        assertSame(error, result.error)
        assertEquals("order-123", result.orderId)
    }

    @Test
    fun `completeCheckoutAuthRequest() reports malformed checkout result`() {
        val result = sut.completeCheckoutAuthRequest(
            Intent().setData(checkoutDeepLink("")),
            checkoutAuthState(orderId = ""),
        ) as PayPalFinishStartResult.Failure

        assertEquals(
            "Result did not contain the expected data. Payer ID or Order ID is null.",
            result.error.errorDescription,
        )
    }

    @Test
    fun `completeVaultAuthRequest() parses successful Intent result`() {
        val intent = Intent().setData(vaultDeepLink("approval-session-123"))

        val result = sut.completeVaultAuthRequest(intent, vaultAuthState())
            as PayPalFinishVaultResult.Success

        assertEquals("approval-session-123", result.approvalSessionId)
    }

    @Test
    fun `completeVaultAuthRequest() parses cancel path from Intent`() {
        val intent = Intent().setData(
            "merchant.app://paypal/vault/cancel?approval_session_id=session-123".toUri()
        )

        val result = sut.completeVaultAuthRequest(intent, vaultAuthState())

        assertSame(PayPalFinishVaultResult.Canceled, result)
    }

    @Test
    fun `completeVaultAuthRequest() maps launch cancellation`() {
        val result = sut.completeVaultAuthRequest(
            PayPalLaunchResult.Canceled,
            vaultAuthState(),
        )

        assertSame(PayPalFinishVaultResult.Canceled, result)
    }

    @Test
    fun `completeVaultAuthRequest() parses successful launcher result`() {
        val result = sut.completeVaultAuthRequest(
            PayPalLaunchResult.Success(vaultDeepLink("approval-session-123")),
            vaultAuthState(),
        ) as PayPalFinishVaultResult.Success

        assertEquals("approval-session-123", result.approvalSessionId)
    }

    @Test
    fun `completeVaultAuthRequest() maps launcher failure`() {
        val error = PayPalSDKError(0, "Auth Tab failed")

        val result = sut.completeVaultAuthRequest(
            PayPalLaunchResult.Failure(error),
            vaultAuthState(),
        ) as PayPalFinishVaultResult.Failure

        assertSame(error, result.error)
    }

    @Test
    fun `completeVaultAuthRequest() reports malformed vault result`() {
        val result = sut.completeVaultAuthRequest(
            Intent().setData(vaultDeepLink("")),
            vaultAuthState(),
        )

        assertTrue(result is PayPalFinishVaultResult.Failure)
    }

    private fun checkoutAuthState(orderId: String = "order-123") = authState(
        requestCode = PAYPAL_CHECKOUT,
        metadata = JSONObject().put("order_id", orderId),
    )

    private fun vaultAuthState() = authState(
        requestCode = PAYPAL_VAULT,
        metadata = JSONObject().put("setup_token_id", "setup-token-123"),
    )

    private fun authState(requestCode: Int, metadata: JSONObject): String {
        val options = BrowserSwitchOptions(
            targetUri = "https://www.paypal.com/checkout".toUri(),
            requestCode = requestCode,
            returnUrlScheme = "merchant.app",
            appLinkUrl = null,
            metadata = metadata,
            launchMode = BrowserSwitchLaunchMode.AUTH_TAB,
        )
        return BrowserSwitchPendingState(options).toBase64EncodedJSON()
    }

    private fun checkoutDeepLink(payerId: String): Uri =
        "merchant.app://paypal/checkout?PayerID=$payerId".toUri()

    private fun vaultDeepLink(approvalSessionId: String): Uri =
        "merchant.app://paypal/vault?approval_session_id=$approvalSessionId".toUri()
}

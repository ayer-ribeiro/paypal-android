package com.paypal.android.paypalpayments

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.browser.auth.AuthTabIntent
import androidx.core.net.toUri
import com.paypal.android.corepayments.browserswitch.BrowserSwitchLaunchMode
import com.paypal.android.corepayments.browserswitch.BrowserSwitchOptions
import com.paypal.android.corepayments.common.DeviceInspector
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LaunchPayPalUnitTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val deviceInspector: DeviceInspector = mockk(relaxed = true)
    private val sut = LaunchPayPal { deviceInspector }

    @Test
    fun `createIntent() creates Auth Tab intent for a custom redirect scheme`() {
        val options = options(returnUrlScheme = "merchant.app")

        val intent = sut.createIntent(context, PayPalAuthChallenge(options))

        assertEquals(options.targetUri, intent.data)
        assertTrue(intent.getBooleanExtra(AuthTabIntent.EXTRA_LAUNCH_AUTH_TAB, false))
        assertEquals(
            "merchant.app",
            intent.getStringExtra(AuthTabIntent.EXTRA_REDIRECT_SCHEME),
        )
        assertNull(intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_HOST))
    }

    @Test
    fun `createIntent() creates Auth Tab intent for an HTTPS App Link`() {
        val options = options(
            returnUrlScheme = null,
            appLinkUrl = "https://merchant.example/paypal/return",
        )

        val intent = sut.createIntent(context, PayPalAuthChallenge(options))

        assertEquals("merchant.example", intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_HOST))
        assertEquals("/paypal/return", intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_PATH))
        assertNull(intent.getStringExtra(AuthTabIntent.EXTRA_REDIRECT_SCHEME))
    }

    @Test
    fun `createIntent() retains Custom Tabs intent for app switch`() {
        val options = options(
            returnUrlScheme = "merchant.app",
            launchMode = BrowserSwitchLaunchMode.CUSTOM_TAB,
        )

        val intent = sut.createIntent(context, PayPalAuthChallenge(options))

        assertEquals(options.targetUri, intent.data)
        assertFalse(intent.getBooleanExtra(AuthTabIntent.EXTRA_LAUNCH_AUTH_TAB, false))
    }

    @Test
    fun `parseResult() maps successful Auth Tab redirect`() {
        val resultUri = "merchant.app://paypal/return?PayerID=payer-id".toUri()

        val result = sut.parseResult(Activity.RESULT_OK, Intent().setData(resultUri))

        assertTrue(result is PayPalLaunchResult.Success)
        assertEquals(resultUri, (result as PayPalLaunchResult.Success).resultUri)
    }

    @Test
    fun `parseResult() maps Auth Tab close to cancellation`() {
        val result = sut.parseResult(Activity.RESULT_CANCELED, null)

        assertSame(PayPalLaunchResult.Canceled, result)
    }

    @Test
    fun `parseResult() maps verification failure`() {
        val result = sut.parseResult(AuthTabIntent.RESULT_VERIFICATION_FAILED, null)

        assertTrue(result is PayPalLaunchResult.Failure)
        assertEquals(
            "Auth Tab failed with result code ${AuthTabIntent.RESULT_VERIFICATION_FAILED}.",
            (result as PayPalLaunchResult.Failure).error.errorDescription,
        )
    }

    @Test
    fun `parseResult() normalizes an unrecognized result code`() {
        val result = sut.parseResult(42, null)

        assertTrue(result is PayPalLaunchResult.Failure)
        assertEquals(
            "Auth Tab failed with result code ${AuthTabIntent.RESULT_UNKNOWN_CODE}.",
            (result as PayPalLaunchResult.Failure).error.errorDescription,
        )
    }

    @Test
    fun `parseResult() treats success without a URI as an unknown failure`() {
        val result = sut.parseResult(Activity.RESULT_OK, null)

        assertTrue(result is PayPalLaunchResult.Failure)
        assertEquals(
            "Auth Tab failed with result code ${AuthTabIntent.RESULT_UNKNOWN_CODE}.",
            (result as PayPalLaunchResult.Failure).error.errorDescription,
        )
    }

    @Test
    fun `getSynchronousResult() reports invalid custom scheme manifest configuration`() {
        every { deviceInspector.isDeepLinkConfiguredInManifest("merchant.app") } returns false

        val result = sut.getSynchronousResult(
            context,
            PayPalAuthChallenge(options(returnUrlScheme = "merchant.app")),
        )?.value

        assertTrue(result is PayPalLaunchResult.Failure)
        assertEquals(
            "This app is not correctly configured to handle deep links from the return url scheme provided.",
            (result as PayPalLaunchResult.Failure).error.errorDescription,
        )
    }

    @Test
    fun `getSynchronousResult() reports an App Link without a host`() {
        val result = sut.getSynchronousResult(
            context,
            PayPalAuthChallenge(
                options(
                    returnUrlScheme = null,
                    appLinkUrl = "not-an-app-link",
                )
            ),
        )?.value

        assertTrue(result is PayPalLaunchResult.Failure)
        assertEquals(
            "The App Link return URL must include a host.",
            (result as PayPalLaunchResult.Failure).error.errorDescription,
        )
    }

    @Test
    fun `getSynchronousResult() reports invalid App Link manifest configuration`() {
        val appLinkUrl = "https://merchant.example/paypal/return"
        every { deviceInspector.isAppLinkConfiguredInManifest(appLinkUrl) } returns false

        val result = sut.getSynchronousResult(
            context,
            PayPalAuthChallenge(
                options(
                    returnUrlScheme = null,
                    appLinkUrl = appLinkUrl,
                )
            ),
        )?.value

        assertTrue(result is PayPalLaunchResult.Failure)
        assertEquals(
            "This app is not correctly configured to handle the provided App Link return URL.",
            (result as PayPalLaunchResult.Failure).error.errorDescription,
        )
    }

    private fun options(
        returnUrlScheme: String?,
        appLinkUrl: String? = null,
        launchMode: BrowserSwitchLaunchMode = BrowserSwitchLaunchMode.AUTH_TAB,
    ) = BrowserSwitchOptions(
        targetUri = "https://www.paypal.com/checkout".toUri(),
        requestCode = 13591,
        returnUrlScheme = returnUrlScheme,
        appLinkUrl = appLinkUrl,
        launchMode = launchMode,
    )
}

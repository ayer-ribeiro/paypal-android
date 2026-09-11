package com.paypal.android.paypalpayments

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContract
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.paypal.android.corepayments.PayPalSDKError
import com.paypal.android.corepayments.browserswitch.BrowserSwitchLaunchMode
import com.paypal.android.corepayments.browserswitch.BrowserSwitchOptions
import com.paypal.android.corepayments.common.DeviceInspector
import com.paypal.android.paypalpayments.errors.PayPalError

/**
 * Activity Result API contract that presents a [PayPalAuthChallenge].
 *
 * Web fallback challenges are fully translated into an AndroidX Auth Tab intent. App-switch
 * challenges retain the existing Custom Tabs intent so Android can resolve the PayPal App Link.
 * Register this contract unconditionally when the Activity or Fragment is created.
 */
class LaunchPayPal internal constructor(
    private val deviceInspectorFactory: (Context) -> DeviceInspector,
) : ActivityResultContract<PayPalAuthChallenge, PayPalLaunchResult>() {

    constructor() : this(::DeviceInspector)

    override fun createIntent(context: Context, input: PayPalAuthChallenge): Intent {
        val options = input.browserSwitchOptions
        return when (options.launchMode) {
            BrowserSwitchLaunchMode.AUTH_TAB -> createAuthTabIntent(options)
            BrowserSwitchLaunchMode.CUSTOM_TAB -> CustomTabsIntent.Builder()
                .build()
                .intent
                .setData(options.targetUri)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PayPalLaunchResult {
        val normalizedResultCode = when (resultCode) {
            AuthTabIntent.RESULT_OK,
            AuthTabIntent.RESULT_CANCELED,
            AuthTabIntent.RESULT_VERIFICATION_FAILED,
            AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT -> resultCode
            else -> AuthTabIntent.RESULT_UNKNOWN_CODE
        }
        return when (normalizedResultCode) {
            AuthTabIntent.RESULT_OK -> intent?.data?.let(PayPalLaunchResult::Success)
                ?: failure(AuthTabIntent.RESULT_UNKNOWN_CODE)

            AuthTabIntent.RESULT_CANCELED -> PayPalLaunchResult.Canceled
            else -> failure(normalizedResultCode)
        }
    }

    override fun getSynchronousResult(
        context: Context,
        input: PayPalAuthChallenge,
    ): SynchronousResult<PayPalLaunchResult>? {
        val options = input.browserSwitchOptions
        val validationError = getValidationError(context, options)
        if (validationError != null) {
            return SynchronousResult(
                PayPalLaunchResult.Failure(PayPalError.browserSwitchError(validationError))
            )
        }

        val launchIntent = createIntent(context, input)
        val resolvedActivity = context.packageManager.resolveActivity(
            launchIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return if (resolvedActivity == null) {
            SynchronousResult(
                PayPalLaunchResult.Failure(
                    PayPalError.browserSwitchError(
                        Exception("Unable to launch PayPal on a device without a web browser.")
                    )
                )
            )
        } else {
            null
        }
    }

    private fun createAuthTabIntent(options: BrowserSwitchOptions): Intent {
        val authTabIntent = AuthTabIntent.Builder().build().intent.apply {
            data = options.targetUri
        }
        val returnUrlScheme = options.returnUrlScheme
        if (returnUrlScheme != null) {
            authTabIntent.putExtra(AuthTabIntent.EXTRA_REDIRECT_SCHEME, returnUrlScheme)
        } else {
            val appLinkUri = requireNotNull(options.appLinkUrl).toUri()
            authTabIntent.putExtra(
                AuthTabIntent.EXTRA_HTTPS_REDIRECT_HOST,
                requireNotNull(appLinkUri.host),
            )
            authTabIntent.putExtra(
                AuthTabIntent.EXTRA_HTTPS_REDIRECT_PATH,
                appLinkUri.path.orEmpty(),
            )
        }
        return authTabIntent
    }

    private fun getValidationError(context: Context, options: BrowserSwitchOptions): Exception? {
        val returnUrlScheme = options.returnUrlScheme
        val appLinkUrl = options.appLinkUrl
        val deviceInspector = deviceInspectorFactory(context)
        return when {
            returnUrlScheme == null && appLinkUrl == null -> Exception(
                "The properties 'returnUrlScheme' and 'appLinkUrl' cannot both be null."
            )

            returnUrlScheme != null &&
                !deviceInspector.isDeepLinkConfiguredInManifest(returnUrlScheme) -> Exception(
                "This app is not correctly configured to handle deep links from the return url scheme provided."
            )

            appLinkUrl != null &&
                appLinkUrl.toUri().host == null -> Exception(
                "The App Link return URL must include a host."
            )

            appLinkUrl != null &&
                !deviceInspector.isAppLinkConfiguredInManifest(appLinkUrl) -> Exception(
                "This app is not correctly configured to handle the provided App Link return URL."
            )

            else -> null
        }
    }

    private fun failure(resultCode: Int) = PayPalLaunchResult.Failure(
        PayPalSDKError(
            code = 0,
            errorDescription = "Auth Tab failed with result code $resultCode.",
        )
    )
}

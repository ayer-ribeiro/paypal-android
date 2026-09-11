package com.paypal.android.paypalpayments

import android.net.Uri
import com.paypal.android.corepayments.PayPalSDKError

/** Result returned after the PayPal approval experience closes or redirects back to the app. */
sealed class PayPalLaunchResult {

    /** PayPal reached the configured return URI. */
    class Success internal constructor(internal val resultUri: Uri) : PayPalLaunchResult()

    /** The buyer closed the approval experience before reaching the return URI. */
    data object Canceled : PayPalLaunchResult()

    /** The approval experience could not be launched or did not return a valid result. */
    data class Failure(val error: PayPalSDKError) : PayPalLaunchResult()
}

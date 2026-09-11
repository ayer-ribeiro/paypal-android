package com.paypal.android.paypalpayments

import com.paypal.android.corepayments.PayPalSDKError

sealed class PayPalPresentAuthChallengeResult {
    class Success internal constructor(
        /** Challenge to pass to the launcher registered with [LaunchPayPal]. */
        val authChallenge: PayPalAuthChallenge,
        internal val authState: String,
    ) : PayPalPresentAuthChallengeResult()

    data class Failure(val error: PayPalSDKError) : PayPalPresentAuthChallengeResult()
}

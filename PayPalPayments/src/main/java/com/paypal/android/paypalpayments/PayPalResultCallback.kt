package com.paypal.android.paypalpayments

import androidx.annotation.MainThread

fun interface PayPalResultCallback {

    /**
     * Called when the PayPal start or vault operation prepares a challenge or fails.
     *
     * Launch the challenge from [PayPalPresentAuthChallengeResult.Success] using [LaunchPayPal].
     *
     * @param result [PayPalPresentAuthChallengeResult] result with details.
     */
    @MainThread
    fun onPayPalResult(result: PayPalPresentAuthChallengeResult)
}

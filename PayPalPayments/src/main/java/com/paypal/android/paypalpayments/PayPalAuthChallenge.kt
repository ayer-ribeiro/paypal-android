package com.paypal.android.paypalpayments

import com.paypal.android.corepayments.browserswitch.BrowserSwitchOptions

/**
 * A request to present the PayPal approval experience.
 *
 * Register [LaunchPayPal] with the Android Activity Result API, then pass this challenge to the
 * returned launcher. Instances are provided by [PayPalPresentAuthChallengeResult.Success].
 */
class PayPalAuthChallenge internal constructor(
    internal val browserSwitchOptions: BrowserSwitchOptions,
)

package com.paypal.android.corepayments.analytics

import android.content.Context
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.paypal.android.corepayments.CoreConfig
import com.paypal.android.corepayments.Environment
import com.paypal.android.corepayments.PayPalSDKError
import com.paypal.android.corepayments.TrackingEventsAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @suppress
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class AnalyticsService internal constructor(
    private val deviceInspector: DeviceInspector,
    private val environment: Environment,
    private val trackingEventsAPI: TrackingEventsAPI,
    private val scope: CoroutineScope
) {

    constructor(context: Context, coreConfig: CoreConfig) :
            this(context, coreConfig, Dispatchers.IO)

    @VisibleForTesting
    internal constructor(
        context: Context,
        coreConfig: CoreConfig,
        dispatcher: CoroutineDispatcher
    ) :
            this(
                DeviceInspector(context),
                coreConfig.environment,
                TrackingEventsAPI(coreConfig),
                CoroutineScope(dispatcher)
            )

    @Suppress("LongParameterList")
    fun sendAnalyticsEvent(
        name: String,
        orderId: String? = null,
        buttonType: String? = null,
        appSwitchEnabled: Boolean = false,
        startTime: Long? = null,
        endTime: Long? = null,
        endpoint: String? = null,
        presentationType: String? = null,
        flow: String? = null
    ) {
        // Log every event as it is dispatched, so events are visible even if the HTTP send fails.
        val details = buildList {
            orderId?.let { add("orderId=$it") }
            buttonType?.let { add("buttonType=$it") }
            add("appSwitchEnabled=$appSwitchEnabled")
            startTime?.let { add("start_time=$it") }
            endTime?.let { add("end_time=$it") }
            endpoint?.let { add("endpoint=$it") }
            presentationType?.let { add("presentation_type=$it") }
            flow?.let { add("flow=$it") }
        }.joinToString(", ")
        Log.d(TAG, "Sending analytics event → $name { $details }")

        // TODO: send analytics event using WorkManager (supports coroutines) to avoid lint error
        // thrown because we don't use the Deferred result
        scope.launch {
            val timestamp = System.currentTimeMillis()
            try {
                val deviceData = deviceInspector.inspect()
                val analyticsEventData = AnalyticsEventData(
                    environment.name.lowercase(),
                    name,
                    timestamp,
                    orderId = orderId,
                    buttonType = buttonType,
                    appSwitchEnabled = appSwitchEnabled,
                    startTime = startTime,
                    endTime = endTime,
                    endpoint = endpoint,
                    presentationType = presentationType,
                    flow = flow
                )
                val response = trackingEventsAPI.sendEvent(analyticsEventData, deviceData)
                val errorMessage = response.error?.message
                if (errorMessage != null) {
                    Log.d(TAG, "Failed to send analytics event '$name': $errorMessage")
                } else {
                    Log.d(TAG, "Analytics event sent ✓ $name (HTTP ${response.status})")
                }
            } catch (e: PayPalSDKError) {
                Log.d(TAG, "Failed to send analytics event '$name' (missing clientId?): ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "[PayPal SDK] Analytics"
    }
}

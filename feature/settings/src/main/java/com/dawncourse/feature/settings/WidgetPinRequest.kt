// QluCampus 0.2.3, GPL-3.0. The launcher accepting a request is not a pin confirmation.
package com.dawncourse.feature.settings

internal enum class WidgetPinStatus { READY, REQUESTED, CONFIRMED, UNAVAILABLE, FAILED }

internal data class WidgetPinRequest(val status: WidgetPinStatus, val token: String? = null)

internal interface WidgetPinHost {
    fun supportsPinning(): Boolean
    fun requestPin(token: String): Boolean
}

internal fun requestWidgetPin(host: WidgetPinHost, token: String): WidgetPinRequest = try {
    if (host.supportsPinning() && host.requestPin(token)) {
        WidgetPinRequest(WidgetPinStatus.REQUESTED, token)
    } else {
        WidgetPinRequest(WidgetPinStatus.UNAVAILABLE)
    }
} catch (_: RuntimeException) {
    WidgetPinRequest(WidgetPinStatus.FAILED)
}

internal fun confirmedWidgetPinStatus(request: WidgetPinRequest, confirmedToken: String?): WidgetPinStatus =
    if (request.status == WidgetPinStatus.REQUESTED && !request.token.isNullOrBlank() && request.token == confirmedToken) {
        WidgetPinStatus.CONFIRMED
    } else request.status

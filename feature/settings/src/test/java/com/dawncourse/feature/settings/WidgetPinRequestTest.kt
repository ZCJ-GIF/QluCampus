package com.dawncourse.feature.settings

import org.junit.Assert.*
import org.junit.Test

class WidgetPinRequestTest {
    private class Host(val supported: Boolean = true, val accepted: Boolean = true, val error: RuntimeException? = null) : WidgetPinHost {
        var requests = 0
        override fun supportsPinning() = supported
        override fun requestPin(token: String): Boolean { requests++; error?.let { throw it }; return accepted }
    }

    @Test fun acceptedRequestMustNotClaimWidgetWasAdded() {
        val attempt = requestWidgetPin(Host(), "new")
        assertEquals(WidgetPinStatus.REQUESTED, attempt.status)
        assertEquals(WidgetPinStatus.REQUESTED, confirmedWidgetPinStatus(attempt, null))
    }
    @Test fun matchingConfirmationReportsSuccess() {
        assertEquals(WidgetPinStatus.CONFIRMED, confirmedWidgetPinStatus(requestWidgetPin(Host(), "new"), "new"))
    }
    @Test fun previousOrDelayedConfirmationDoesNotConfirmAnotherRequest() {
        val attempt = requestWidgetPin(Host(), "new")
        assertEquals(WidgetPinStatus.REQUESTED, confirmedWidgetPinStatus(attempt, "old"))
    }
    @Test fun unsupportedLauncherDoesNotReceiveRequest() {
        val host = Host(supported = false)
        assertEquals(WidgetPinStatus.UNAVAILABLE, requestWidgetPin(host, "new").status)
        assertEquals(0, host.requests)
    }
    @Test fun launcherCanRejectDespiteReportingSupport() {
        assertEquals(WidgetPinStatus.UNAVAILABLE, requestWidgetPin(Host(accepted = false), "new").status)
    }
    @Test fun foregroundOrSecurityRestrictionBecomesRecoverableFailure() {
        for (error in listOf(IllegalStateException("background"), SecurityException("restricted"))) {
            assertEquals(WidgetPinStatus.FAILED, requestWidgetPin(Host(error = error), "new").status)
        }
    }
    @Test fun unavailableRequestCannotBeConfirmedByMissingToken() {
        assertEquals(WidgetPinStatus.UNAVAILABLE, confirmedWidgetPinStatus(WidgetPinRequest(WidgetPinStatus.UNAVAILABLE), null))
    }
}

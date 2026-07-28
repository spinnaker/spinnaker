package com.netflix.spinnaker.keel.retrofit

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

/**
 * Okhttp3 interceptor that adds the X-SPINNAKER-* headers to enable authorization and tracing with downstream
 * Spinnaker services.
 */
class SpinnakerHeadersInterceptor : Interceptor {
  override fun intercept(chain: Chain): Response {
    var request = chain.request()
    val headers = mutableMapOf<String, String>()

    // say we're calling from keel
    headers[Header.USER_ORIGIN.header] = "keel"

    // generate request ID for tracing
    AuthenticatedRequest.getSpinnakerRequestId().ifPresent { id ->
      headers[Header.REQUEST_ID.header] = id
    }

    // Historically keel attached an X-SPINNAKER-ACCOUNTS header here, resolving the caller's account
    // permissions from Fiat, so downstream services had a fallback when Fiat was unavailable. Fiat
    // has since been removed: downstream services now derive permissions from the verified identity
    // token, so no account header is attached here.

    request = request.newBuilder().let { builder ->
      headers.forEach { (header, value) ->
        builder.addHeader(header, value)
      }
      builder.build()
    }

    return chain.proceed(request)
  }
}

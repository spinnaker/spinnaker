package com.netflix.spinnaker.keel.retrofit

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * Okhttp3 interceptor that adds the X-SPINNAKER-* headers to enable authorization and tracing with downstream
 * Spinnaker services.
 */
class SpinnakerHeadersInterceptor : Interceptor {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun intercept(chain: Chain): Response {
    var request = chain.request()
    val headers = mutableMapOf<String, String>()

    // say we're calling from keel
    headers[Header.USER_ORIGIN.header] = "keel"

    // generate request ID for tracing
    AuthenticatedRequest.getSpinnakerRequestId().ifPresent { id ->
      headers[Header.REQUEST_ID.header] = id
    }

    // add account information so that downstream services can use that as a fallback if fiat is down
    request.header(Header.USER.header)?.also { user ->
      // TODO: move the call to fiat to retrieve account permission up in the stack to avoid circular dependency
      //  with new OkHttpClient setup in kork.
      /*
      AuthenticatedRequest.allowAnonymous {
        val accounts = fiatPermissionEvaluator.getPermission(user).accounts.joinToString(",") { it.name }
        log.trace("Adding X-SPINNAKER-ACCOUNTS: $accounts to ${request.method} ${request.url}")
        headers[Header.ACCOUNTS.header] = accounts
      }
      */
    }

    request = request.newBuilder().let { builder ->
      headers.forEach { (header, value) ->
        builder.addHeader(header, value)
      }
      builder.build()
    }

    return chain.proceed(request)
  }
}

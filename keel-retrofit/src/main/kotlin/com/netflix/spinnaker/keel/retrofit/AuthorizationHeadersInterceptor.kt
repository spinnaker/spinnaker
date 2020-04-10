package com.netflix.spinnaker.keel.retrofit

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import org.slf4j.LoggerFactory

/**
 * Okhttp3 interceptor that adds the X-SPINNAKER-USER and X-SPINNAKER-ACCOUNTS headers to enable authorization
 * with downstream Spinnaker services.
 */
class AuthorizationHeadersInterceptor(private val fiatPermissionEvaluator: FiatPermissionEvaluator) : Interceptor {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun intercept(chain: Chain): Response {
    var request = chain.request()
    request.header(Header.USER.header)?.also { user ->
      AuthenticatedRequest.allowAnonymous {
        val accounts = fiatPermissionEvaluator.getPermission(user).accounts.joinToString(",") { it.name }
        log.trace("Adding X-SPINNAKER-ACCOUNTS: $accounts to ${request.method} ${request.url}")
        request = request
          .newBuilder()
          .addHeader(Header.ACCOUNTS.header, accounts)
          .build()
      }
    }
    return chain.proceed(request)
  }
}

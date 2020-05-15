package com.netflix.spinnaker.keel.retrofit

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Okhttp3 interceptor that adds User-Agent header.
 */
class UserAgentInterceptor(private val keelRetrofitProperties: KeelRetrofitProperties) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request().newBuilder()
      .header("User-Agent", keelRetrofitProperties.userAgent)
      .build()

    return chain.proceed(request)
  }
}

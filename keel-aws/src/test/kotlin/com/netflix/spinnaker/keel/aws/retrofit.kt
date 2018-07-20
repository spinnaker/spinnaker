package com.netflix.spinnaker.keel.aws

import retrofit.RetrofitError
import retrofit.client.Response

val RETROFIT_NOT_FOUND: RetrofitError = RetrofitError
  .httpError(
    "http://example.com",
    Response("http://example.com", 404, "Not Found", listOf(), null),
    null,
    null
  )

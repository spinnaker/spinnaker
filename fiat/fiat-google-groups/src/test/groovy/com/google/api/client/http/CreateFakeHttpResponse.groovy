package com.google.api.client.http

import com.google.api.client.testing.http.MockLowLevelHttpResponse

/**
 * Creates a fake HttpResponse for testing purposes.  Yes this has to be here... because the danged request/response objects are not mockable, NOR public and are FINAL.  So to do SIMPLE testing
 * of logic like retry handling on a bad response... we need something like this.
 */
class CreateFakeHttpResponse {

  static HttpResponse create(int statusCode) {
    return new HttpResponse(new HttpRequest(null, null), new MockLowLevelHttpResponse().setStatusCode(statusCode))
  }
}

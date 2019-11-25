package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.rest.OkHttpClientFactory
import com.netflix.spinnaker.echo.rest.RestClientFactory
import com.squareup.okhttp.OkHttpClient
import spock.lang.Specification
import spock.lang.Subject

class RestClientFactorySpec extends Specification{

  @Subject
  RestClientFactory clientFactory = new RestClientFactory()

  Boolean insecure

  OkHttpClientFactory httpClientFactory

  void setup() {
    httpClientFactory = Mock(OkHttpClientFactory)
    clientFactory.httpClientFactory = httpClientFactory
  }

  void 'returns insecure client'() {
    given:
    insecure = true

    when:
    clientFactory.getClient(insecure)

    then:
    1 * httpClientFactory.getInsecureClient() >> new OkHttpClient()
  }

   void 'returns secure client'() {
    given:
    insecure = false

    when:
    clientFactory.getClient(insecure)

    then:
    0 * httpClientFactory.getInsecureClient() >> new OkHttpClient()
  }
}

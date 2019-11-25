package com.netflix.spinnaker.echo.events

import com.netflix.spinnaker.echo.rest.OkHttpClientFactory
import com.netflix.spinnaker.echo.rest.OkHttpClientFactoryImpl
import com.squareup.okhttp.OkHttpClient
import spock.lang.Specification
import spock.lang.Subject

class OkHttpClientFactorySpec extends Specification {

  @Subject
  OkHttpClientFactory clientFactory = new OkHttpClientFactoryImpl()
  OkHttpClient insecureClient

  void 'insecure client does not verify hostname'() {

    when:
    insecureClient = clientFactory.getInsecureClient()

    then:
    insecureClient.getHostnameVerifier().verify("mockdomain", null)

  }
}

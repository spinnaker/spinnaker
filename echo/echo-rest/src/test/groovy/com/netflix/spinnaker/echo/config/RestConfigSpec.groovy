package com.netflix.spinnaker.echo.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.RequestListener
import com.github.tomakehurst.wiremock.http.Response
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.config.okhttp3.InsecureOkHttpClientBuilderProvider
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig
import com.netflix.spinnaker.echo.test.config.Retrofit2BasicLogTestConfig
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

@SpringBootTest(classes = [Retrofit2TestConfig, Retrofit2BasicLogTestConfig],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RestConfigSpec extends Specification {

  @Subject config = new RestConfig()
  @Subject BlockingVariable<Map<String, String>> headers
  def EmptyHeadersFile = Mock(RestConfig.HeadersFromFile)

  WireMockServer wireMockServer

  @Autowired
  OkHttp3ClientConfiguration okHttpClientConfig


  RestUrls configureRestServices(RestProperties.RestEndpointConfiguration endpoint, RestConfig.HeadersFromFile headersFromFile) {
    RestProperties restProperties =  new RestProperties(endpoints: [endpoint])
    return config.restServices(restProperties, new OkHttpClientProvider([new InsecureOkHttpClientBuilderProvider(new OkHttpClient())], new Retrofit2EncodeCorrectionInterceptor()), okHttpClientConfig, headersFromFile)
  }

  def setup() {
    headers = new BlockingVariable<Map<String, String>>(5)
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    wireMockServer.addMockServiceRequestListener(new RequestListener() {

      @Override
      void requestReceived(Request request, Response response) {
        headers.set(request.getHeaders().all().collectEntries { header ->
          [(header.key()): header.values().join(',')]})
      }
    });

    wireMockServer.start();

    wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/"))
      .willReturn(WireMock.aResponse()
        .withStatus(200)))
  }

  def cleanup(){
    wireMockServer.stop()
  }

  void "Generate basic auth header"() {
    given:
    RestProperties.RestEndpointConfiguration endpoint = new RestProperties.RestEndpointConfiguration(
      url: wireMockServer.baseUrl(),
      username: "testuser",
      password: "testpassword")
    RestUrls restUrls = configureRestServices(endpoint, EmptyHeadersFile)

    when:
    Retrofit2SyncCall.execute(restUrls.getServices().get(0).getClient().recordEvent([:]))

    then:
    headers.get().get("Authorization") == "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk"
  }

  void "'Authorization' header over generated basic auth header"() {
    given:
    RestProperties.RestEndpointConfiguration endpoint = new RestProperties.RestEndpointConfiguration(
      url: wireMockServer.baseUrl(),
      username: "testuser",
      password: "testpassword",
      headers: ["Authorization": "FromConfig"])
    RestUrls restUrls = configureRestServices(endpoint, EmptyHeadersFile)

    when:
    Retrofit2SyncCall.execute(restUrls.getServices().get(0).getClient().recordEvent([:]))

    then:
    headers.get().get("Authorization") == "FromConfig"
  }

  void "'Authorization' headerFile over all others"() {
    given:
    RestProperties.RestEndpointConfiguration endpoint = new RestProperties.RestEndpointConfiguration(
      url: wireMockServer.baseUrl(),
      username: "testuser",
      password: "testpassword",
      headers: ["Authorization": "FromConfig"],
      headersFile: "/testfile")
    RestConfig.HeadersFromFile headersFromFile = new RestConfig.HeadersFromFile() {
      @Override
      Map<String, String> headers(String path) {
        return [
          "Authorization": "FromFile"
        ]
      }
    }
    RestUrls restUrls = configureRestServices(endpoint, headersFromFile)

    when:
    Retrofit2SyncCall.execute(restUrls.getServices().get(0).getClient().recordEvent([:]))

    then:
    headers.get().get("Authorization") == "FromFile"
  }
}

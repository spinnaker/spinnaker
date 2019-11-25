package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.echo.rest.RestClientFactory
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import spock.lang.Specification
import spock.lang.Subject

class RestConfigSpec extends Specification {

  @Subject
  config = new RestConfig()

  def request = Mock(RequestInterceptor.RequestFacade)
  def EmptyHeadersFile = Mock(RestConfig.HeadersFromFile)
  def attacher = new RestConfig.RequestInterceptorAttacher() {
    RequestInterceptor interceptor
    @Override
    public void attach(RestAdapter.Builder builder, RequestInterceptor interceptor) {
      this.interceptor = interceptor
    }
  }

  void configureRestServices(RestProperties.RestEndpointConfiguration endpoint, RestConfig.HeadersFromFile headersFromFile) {
    RestProperties restProperties =  new RestProperties(endpoints: [endpoint])
    config.restServices(restProperties, new RestClientFactory(), config.retrofitLogLevel("BASIC"), attacher, headersFromFile)
  }

  void "Generate basic auth header"() {
    given:
    RestProperties.RestEndpointConfiguration endpoint = new RestProperties.RestEndpointConfiguration(
      url: "http://localhost:9090",
      username: "testuser",
      password: "testpassword")
    configureRestServices(endpoint, EmptyHeadersFile)

    when:
    attacher.interceptor.intercept(request)

    then:
    1 * request.addHeader("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk")
    0 * request.addHeader(_, _)
  }

  void "'Authorization' header over generated basic auth header"() {
    given:
    RestProperties.RestEndpointConfiguration endpoint = new RestProperties.RestEndpointConfiguration(
      url: "http://localhost:9090",
      username: "testuser",
      password: "testpassword",
      headers: ["Authorization": "FromConfig"])
    configureRestServices(endpoint, EmptyHeadersFile)

    when:
    attacher.interceptor.intercept(request)

    then:
    1 * request.addHeader("Authorization", "FromConfig")
    0 * request.addHeader(_, _)
  }

  void "'Authorization' headerFile over all others"() {
    given:
    RestProperties.RestEndpointConfiguration endpoint = new RestProperties.RestEndpointConfiguration(
      url: "http://localhost:9090",
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
    configureRestServices(endpoint, headersFromFile)

    when:
    attacher.interceptor.intercept(request)

    then:
    1 * request.addHeader("Authorization", "FromFile")
    0 * request.addHeader(_, _)
  }
}

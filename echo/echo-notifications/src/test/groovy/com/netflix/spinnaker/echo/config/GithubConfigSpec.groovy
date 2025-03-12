package com.netflix.spinnaker.echo.config

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.echo.github.GithubService
import com.netflix.spinnaker.echo.test.config.Retrofit2BasicLogTestConfig
import com.netflix.spinnaker.echo.test.config.Retrofit2HeadersLogTestConfig
import com.netflix.spinnaker.echo.test.config.Retrofit2NoneLogTestConfig
import com.netflix.spinnaker.echo.test.config.Retrofit2TestConfig
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest(
  classes = [Retrofit2TestConfig, Retrofit2BasicLogTestConfig],
  properties = ["github-status.enabled=true"],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GithubConfigLogLevelBasicSpec extends Specification {

  @Autowired
  OkHttp3ClientConfiguration okHttpClientConfig

  WireMockServer wireMockServer
  GithubService ghService
  PrintStream systemError
  ByteArrayOutputStream testErr
  int port

  def setup() {
    systemError = System.out;
    testErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testErr))

    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort())
    wireMockServer.start()

    port = wireMockServer.port()

    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/repos/repo-name/commits/sha12345"))
      .willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"message\": \"response\", \"code\": 200}")));

    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/repos//commits/"))
      .willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"message\": \"response\", \"code\": 200}")));

    GithubConfig config = new GithubConfig(wireMockServer.baseUrl())
    ghService = config.githubService(okHttpClientConfig)

  }

  def cleanup() {
    wireMockServer.stop()
    System.setOut(systemError)
    System.out.print(testErr)
  }



  def 'default log level does not output authorization headers and matches basic API call structure'() {
    when:

    Retrofit2SyncCall.execute(ghService.getCommit("SECRET", "repo-name", "sha12345"))

    then:
    def logOutput = testErr.toString()
    logOutput.contains("--> GET http://localhost:" + port + "/repos/repo-name/commits/sha12345")
    !logOutput.contains("SECRET")
    !logOutput.contains("Authorization")

  }
}

@SpringBootTest(
  classes = [Retrofit2TestConfig, Retrofit2NoneLogTestConfig],
  properties = ["github-status.enabled=true"],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GithubConfigLogLevelNoneSpec extends Specification {

  @Autowired
  OkHttp3ClientConfiguration okHttpClientConfig

  WireMockServer wireMockServer
  GithubService ghService
  PrintStream systemError
  ByteArrayOutputStream testErr

  def setup() {
    systemError = System.out;
    testErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testErr))

    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort())
    wireMockServer.start()

    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/repos//commits/"))
      .willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"message\": \"response\", \"code\": 200}")));

    GithubConfig config = new GithubConfig(wireMockServer.baseUrl())
    ghService = config.githubService(okHttpClientConfig)
  }

  def cleanup() {
    wireMockServer.stop()
    System.setOut(systemError)
    System.out.print(testErr)
  }

  def 'When no log set, no log output!'() {
    when:
    Retrofit2SyncCall.execute(ghService.getCommit("", "", ""));

    then:
    !testErr.toString().contains("--> GET")
  }
}

@SpringBootTest(
  classes = [Retrofit2TestConfig, Retrofit2HeadersLogTestConfig],
  properties = ["github-status.enabled=true"],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GithubConfigLogLevelHeadersSpec extends Specification {

  @Autowired
  OkHttp3ClientConfiguration okHttpClientConfig

  WireMockServer wireMockServer
  GithubService ghService
  PrintStream systemError
  ByteArrayOutputStream testErr

  def setup() {
    systemError = System.out;
    testErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testErr))

    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort())
    wireMockServer.start()

    wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/repos//commits/"))
      .willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"message\": \"response\", \"code\": 200}")));

    GithubConfig config = new GithubConfig(wireMockServer.baseUrl())
    ghService = config.githubService(okHttpClientConfig)

  }

  def cleanup() {
    wireMockServer.stop()
    System.setOut(systemError)
    System.out.print(testErr)
  }

  def 'Log when full has header information and auth headers- dont do this in prod!'() {
    when:
    Retrofit2SyncCall.execute(ghService.getCommit("", "", ""));

    then:
    testErr.toString().contains("Authorization")
  }
}

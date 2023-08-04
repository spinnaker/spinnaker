package com.netflix.spinnaker.echo.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import retrofit.Endpoint
import retrofit.Endpoints
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.client.Header
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject

@SpringBootTest(
  classes = [GithubConfig.class, MockRetrofitConfig.class],
  properties = ["github-status.enabled=true"],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GithubConfigSpec extends Specification {
  @Autowired
  @Subject
  GithubConfig githubConfig

  def 'test github endpoint default is not wrapped in single quotes'() {
    given:
    String ownEndpoint = "'https://api.github.com'"

    when:
    Endpoint endpoint = githubConfig.githubEndpoint()

    then:
    endpoint.url != ownEndpoint
  }


  def 'test github endpoint default is set'() {
    given:
    String ownEndpoint = "https://api.github.com"

    when:
    Endpoint endpoint = githubConfig.githubEndpoint()

    then:
    endpoint.url == ownEndpoint
  }


  def 'default log level does not output authorization headers and matches basic API call structure'() {
    given:
    def systemError = System.out;
    def testErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testErr))

    Client mockClient = Stub(Client) {
      execute(_) >> {
        return new Response("http://example.com", 200, "Success!", new ArrayList<Header>(), new TypedByteArray("", "SOmething workedddd".bytes))
      }

    }
    def ghService = new GithubConfig().githubService(Endpoints.newFixedEndpoint("http://example.com"), mockClient, null)

    when:
    ghService.getCommit("SECRET", "repo-name", "sha12345");

    then:
    def logOutput = testErr.toString()
    logOutput.contains("HTTP GET http://example.com/repos/repo-name/commits/sha12345")
    !logOutput.contains("SECRET")
    !logOutput.contains("Authorization")

    cleanup:
    System.setOut(systemError)
    System.out.print(testErr)
  }

  def 'When no log set, no log output!'() {
    given:
    def systemError = System.out;
    def testErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testErr))

    Client mockClient = Stub(Client) {
      execute(_) >> new Response("http://example.com", 200, "Ok", new ArrayList<Header>(), new TypedByteArray("", "response".bytes))
    }
    def ghService = new GithubConfig().githubService(Endpoints.newFixedEndpoint("http://example.com"), mockClient, RestAdapter.LogLevel.NONE)

    when:
    ghService.getCommit("", "", "");

    then:
    !testErr.toString().contains("GET")

    cleanup:
    System.setOut(systemError)
    System.out.print(testErr)
  }

  def 'Log when full has header information and auth headers- dont do this in prod!'() {
    given:
    def systemError = System.out;
    def testErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(testErr))

    Client mockClient = Stub(Client) {
      execute(_) >> new Response("http://example.com", 200, "Ok", new ArrayList<Header>(), new TypedByteArray("", "response".bytes))
    }
    def ghService = new GithubConfig().githubService(Endpoints.newFixedEndpoint("http://example.com"), mockClient, RestAdapter.LogLevel.FULL)

    when:
    ghService.getCommit("", "", "");

    then:
    testErr.toString().contains("Authorization")

    cleanup:
    System.setOut(systemError)
    System.out.print(testErr)
  }
}

@SpringBootTest(
  classes = [GithubConfig.class, MockRetrofitConfig.class],
  properties = [
    "github-status.enabled=true",
    "github-status.endpoint=https://my.github.com"
  ],
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GithubConfigEndpointSetSpec extends Specification {
  @Autowired
  @Subject
  GithubConfig githubConfig

  def 'test github endpoint in config is set'() {
    given:
    String ownEndpoint = "https://my.github.com"

    when:
    Endpoint endpoint = githubConfig.githubEndpoint()

    then:
    endpoint.url == ownEndpoint
  }
}

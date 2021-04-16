package com.netflix.spinnaker.echo.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import retrofit.Endpoint
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

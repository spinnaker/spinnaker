package com.netflix.spinnaker.echo.config

import spock.lang.Specification
import spock.lang.Subject
import retrofit.Endpoint

class GithubConfigSpec extends Specification {
  @Subject
  GithubConfig githubConfig = new GithubConfig()

  def 'test github incoming endpoint is correctly setted'() {
    given:
    String ownEndpoint = "https://github.myendpoint.com"
    githubConfig.endpoint = ownEndpoint;

    when:
    Endpoint endpoint = githubConfig.githubEndpoint()

    then:
    endpoint.url == ownEndpoint
  }

}

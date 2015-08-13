package com.netflix.spinnaker.echo.services

import retrofit.Endpoints
import retrofit.RestAdapter
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.echo.model.Pipeline
import retrofit.client.Client
import retrofit.client.Request
import retrofit.client.Response
import retrofit.mime.TypedString

class Front50ServiceSpec extends Specification {
  def endpoint = "http://front50-prestaging.prod.netflix.net"
  def client = Stub(Client) {
    execute(_) >> { Request request ->
      new Response(
        request.url, 200, "OK", [],
        new TypedString(getClass().getResourceAsStream("/pipelines.json").text)
      )
    }
  }
  @Subject front50 = new RestAdapter.Builder()
    .setEndpoint(Endpoints.newFixedEndpoint(endpoint))
    .setClient(client)
    .build()
    .create(Front50Service)

  def "parses pipelines"() {
    when:
    def pipelines = front50.getPipelines().toBlocking().first()

    then:
    !pipelines.empty
  }

  def "handles pipelines with empty triggers array"() {
    when:
    def pipelines = front50.getPipelines().toBlocking().first()


    then:
    def pipeline = pipelines.find { it.application == "pond" }
    pipeline.name == "deploy to prestaging"
    pipeline.triggers.empty
  }

  def "handles pipelines with actual triggers"() {
    when:
    def pipelines = front50.getPipelines().toBlocking().first()

    then:
    def pipeline = pipelines.find { it.application == "rush" && it.name == "bob the sinner" }
    pipeline.triggers.size() == 1
    with(pipeline.triggers[0]) {
      enabled
      type == "jenkins"
      master == "spinnaker"
      job == "Dummy_test_job"
      propertyFile == "deb.properties"
    }
  }

  @Ignore
  def "list properties are immutable"() {
    given:
    def pipelines = front50.getPipelines().toBlocking().first()
    def pipeline = pipelines.find { it.application == "kato" }

    expect:
    pipeline.triggers instanceof ImmutableList

    when:
    pipeline.triggers << new Pipeline.Trigger(false, "foo", "bar", "baz")

    then:
    thrown UnsupportedOperationException
  }

}

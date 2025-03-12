package com.netflix.spinnaker.clouddriver.eureka.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.eureka.api.EurekaApi
import com.netflix.spinnaker.clouddriver.eureka.model.DataCenterInfo
import com.netflix.spinnaker.clouddriver.eureka.model.DataCenterMetadata
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplication
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplications
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaInstance
import com.netflix.spinnaker.clouddriver.model.HealthState
import retrofit2.mock.Calls
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES

class EurekaCachingAgentSpec extends Specification {
  def providerCache = Stub(ProviderCache)
  def eurekaApi = Stub(EurekaApi)
  def eap = new TestEurekaAwareProvider()

  def agent = new EurekaCachingAgent(eurekaApi, "us-foo-2", new ObjectMapper(), "http://eureka", "true", "eureka-foo", [eap], 0, 0)

  def "it should cache instances"() {
    given:
    eurekaApi.loadEurekaApplications() >> Calls.response(new EurekaApplications(applications: [
        new EurekaApplication(name: "foo", instances: [
        instance("foo", "i-1", "UP"),
        instance("foo", "i-2", "UP")
      ])
    ]))


    when:
    def result = agent.loadData(providerCache)

    then:
    result.cacheResults.size() == 2
    result.cacheResults[HEALTH.ns].size() == 2
    result.cacheResults[INSTANCES.ns].size() == 2
    result.cacheResults[HEALTH.ns]*.id.sort() == ["us-foo-2:i-1:Discovery", "us-foo-2:i-2:Discovery"]
    result.cacheResults[INSTANCES.ns]*.id.sort() == ["us-foo-2:i-1", "us-foo-2:i-2"]
  }

  def "it should dedupe multiple discovery records prefering HealthState order"() {
    given:
    eurekaApi.loadEurekaApplications() >> Calls.response(new EurekaApplications(applications: [
      new EurekaApplication(name: "foo", instances: [
        instance("foo", "i-1", "UP"),
        instance("foo", "i-1", "DOWN")
      ])
    ]))

    when:
    def result = agent.loadData(providerCache)

    then:
    result.cacheResults.size() == 2
    result.cacheResults[HEALTH.ns].size() == 1
    result.cacheResults[INSTANCES.ns].size() == 1
    result.cacheResults[HEALTH.ns].first().attributes.state == HealthState.Down.name()

  }

  def "it should dedupe multiple discovery records preferring newest"() {
    given:
    eurekaApi.loadEurekaApplications() >> Calls.response(new EurekaApplications(applications: [
      new EurekaApplication(name: "foo", instances: [
        instance("foo", "i-1", "UP", 12345),
        instance("foo", "i-1", "UP", 23451),
        instance("foo", "i-1", "UP", 12344)
      ])
    ]))

    when:
    def result = agent.loadData(providerCache)

    then:
    result.cacheResults.size() == 2
    result.cacheResults[HEALTH.ns].size() == 1
    result.cacheResults[INSTANCES.ns].size() == 1
    result.cacheResults[HEALTH.ns].first().attributes.lastUpdatedTimestamp == 23451

  }

  private static EurekaInstance instance(String app, String id, String status, Long timestamp = System.currentTimeMillis()) {
    EurekaInstance.buildInstance(
      "host",
      app,
      "127.0.0.1",
      status,
      "UNKNOWN",
      new DataCenterInfo(
        name: "my-dc",
        metadata: new DataCenterMetadata(
          accountId: "foo",
          availabilityZone: "us-foo-2a",
          amiId: "ami-foo",
          instanceId: id,
          instanceType: "m3.megabig")),
      "/status",
      "/healthcheck",
      id,
      id,
      timestamp,
      "$app-v000",
      null,
      id)
  }

  static class TestEurekaAwareProvider implements EurekaAwareProvider {
    @Override
    Boolean isProviderForEurekaRecord(Map<String, Object> attributes) {
      return true
    }

    @Override
    String getInstanceKey(Map<String, Object> attributes, String region) {
      return "$region:$attributes.instanceId"
    }

    @Override
    String getInstanceHealthKey(Map<String, Object> attributes, String region, String healthId) {
      return "$region:$attributes.instanceId:$healthId"
    }
  }
}

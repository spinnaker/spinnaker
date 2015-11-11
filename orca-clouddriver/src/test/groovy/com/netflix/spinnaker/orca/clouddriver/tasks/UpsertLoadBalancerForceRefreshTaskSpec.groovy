package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject
  def task = new UpsertLoadBalancerForceRefreshTask()
  def stage = new PipelineStage(type: "whatever")

  def config = [
    targets: [
      [credentials: "fzlem", availabilityZones: ["us-west-1": []], name: "flapjack-frontend"]
    ]
  ]

  def setup() {
    stage.context.putAll(config)
  }

  void "should force cache refresh server groups via oort when name provided"() {
    setup:
    task.oort = Mock(OortService)

    when:
    task.execute(stage.asImmutable())

    then:
    1 * task.oort.forceCacheUpdate(UpsertLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.loadBalancerName == "flapjack-frontend"
      assert body.account == "fzlem"
      assert body.region == "us-west-1"
    }
  }
}

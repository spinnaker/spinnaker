package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject

class UpsertAmazonLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject
  def task = new UpsertAmazonLoadBalancerForceRefreshTask()
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
    1 * task.oort.forceCacheUpdate(UpsertAmazonLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.loadBalancerName == "flapjack-frontend"
      assert body.account == "fzlem"
      assert body.region == "us-west-1"
    }
  }
}

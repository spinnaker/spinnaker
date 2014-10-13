package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.oort.OortService

/**
 * Created by aglover on 9/29/14.
 */
class UpsertAmazonLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject task = new UpsertAmazonLoadBalancerForceRefreshTask()
  def context = new SimpleTaskContext()

  def config = [
    "account.name"  : "fzlem",
    region          : ["us-west-1"],
    credentials     : "fzlem"
  ]

  def setup() {
    config.each {
      context."upsertAmazonLoadBalancer.${it.key}" = it.value
    }
  }

  void "should force cache refresh server groups via oort when clusterName provided"() {
    setup:
    def name = "flapjack"
    context."upsertAmazonLoadBalancer.clusterName" = name
    task.oort = Mock(OortService)

    when:
    task.execute(context)

    then:
    1 * task.oort.forceCacheUpdate(UpsertAmazonLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.loadBalancerName == "$name-frontend"
      assert body.account == config."account.name"
      assert body.region == "us-west-1"
    }
  }

  void "should force cache refresh server groups via oort when name provided"() {
    setup:
    def name = "flapjack-frontend"
    context."upsertAmazonLoadBalancer.name" = name
    task.oort = Mock(OortService)

    when:
    task.execute(context)

    then:
    1 * task.oort.forceCacheUpdate(UpsertAmazonLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.loadBalancerName == name
      assert body.account == config."account.name"
      assert body.region == "us-west-1"
    }
  }
}

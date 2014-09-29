package com.netflix.spinnaker.orca.kato.tasks

import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.oort.OortService

/**
 * Created by aglover on 9/29/14.
 */
class AmazonLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject task = new AmazonLoadBalancerForceRefreshTask()
  def context = new SimpleTaskContext()

  def config = [
    "account.name"  : "fzlem",
    loadBalancerName: "flapjack-frontend",
    regions         : ["us-west-1"],
    credentials     : "fzlem"
  ]

  def setup() {
    config.each {
      context."deleteAmazonLoadBalancer.${it.key}" = it.value
    }
  }

  void "should force cache refresh server groups via oort"() {
    setup:
    task.oort = Mock(OortService)

    when:
    task.execute(context)

    then:
    1 * task.oort.forceCacheUpdate(AmazonLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.loadBalancerName == config."loadBalancerName"
      assert body.account == config."account.name"
      assert body.region == "us-west-1"
    }
  }
}

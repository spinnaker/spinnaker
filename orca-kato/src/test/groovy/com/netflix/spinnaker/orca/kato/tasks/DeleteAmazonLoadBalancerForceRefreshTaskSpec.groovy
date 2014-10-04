/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.oort.OortService
import spock.lang.Specification
import spock.lang.Subject

class DeleteAmazonLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject task = new DeleteAmazonLoadBalancerForceRefreshTask()
  def context = new SimpleTaskContext()

  def config = [
    regions         : ["us-west-1"],
    credentials     : "fzlem",
    loadBalancerName: 'flapjack-main-frontend'
  ]

  def setup() {
    config.each {
      context."deleteAmazonLoadBalancer.${it.key}" = it.value
    }
  }

  void "should force cache refresh server groups via oort when clusterName provided"() {
    setup:
    task.oort = Mock(OortService)

    when:
    task.execute(context)

    then:
    1 * task.oort.forceCacheUpdate(DeleteAmazonLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.loadBalancerName == config.loadBalancerName
      assert body.account == config.credentials
      assert body.region == "us-west-1"
      assert body.evict == true
    }
  }
}

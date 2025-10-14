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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeleteLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject task = new DeleteLoadBalancerForceRefreshTask()
  def stage = stage()

  def config = [
    cloudProvider   : 'aws',
    regions         : ["us-west-1"],
    credentials     : "fzlem",
    loadBalancerName: 'flapjack-main-frontend'
  ]

  def setup() {
    stage.context.putAll(config)
  }

  void "should force cache refresh server groups via oort when clusterName provided"() {
    setup:
    task.cacheService = Mock(CloudDriverCacheService)

    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate(stage.context.cloudProvider, DeleteLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, Object> body ->

      assert body.loadBalancerName == config.loadBalancerName
      assert body.account == config.credentials
      assert body.region == "us-west-1"
      assert body.evict == true
    }
  }
}

/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

class UpsertLoadBalancerForceRefreshTaskSpec extends Specification {
  @Subject
  def task = new UpsertLoadBalancerForceRefreshTask()
  def stage = stage()

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
    task.cacheService = Mock(CloudDriverCacheService)

    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', UpsertLoadBalancerForceRefreshTask.REFRESH_TYPE, _) >> { String cloudProvider, String type, Map<String, ? extends Object> body ->
      assert cloudProvider == "aws"
      assert body.loadBalancerName == "flapjack-frontend"
      assert body.account == "fzlem"
      assert body.region == "us-west-1"
    }
  }
}

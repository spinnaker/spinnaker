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

package com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup

import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class SecurityGroupForceCacheRefreshTaskSpec extends Specification {
  @Subject task = new SecurityGroupForceCacheRefreshTask()
  def stage = new Stage<>(type: "whatever")

  def config = [
    name       : "sg-12345a",
    region     : "us-west-1",
    accountName: "fzlem"
  ]

  def setup() {
    stage.context.targets = [
        config
    ]
  }

  void "should force cache refresh security groups via mort"() {
    setup:
    task.cacheService = Mock(CloudDriverCacheService)

    when:
    task.execute(stage)

    then:
    1 * task.cacheService.forceCacheUpdate('aws', SecurityGroupForceCacheRefreshTask.REFRESH_TYPE, _) >> {
      String cloudProvider, String type, Map<String, ? extends Object> body ->

      assert body.securityGroupName == config.name
      assert body.account == config.accountName
      assert body.region == "us-west-1"
    }
  }
}

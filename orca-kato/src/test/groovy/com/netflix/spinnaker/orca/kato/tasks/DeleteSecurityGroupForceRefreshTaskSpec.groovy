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

import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage

class DeleteSecurityGroupForceRefreshTaskSpec extends Specification {
  @Subject task = new DeleteSecurityGroupForceRefreshTask()
  def stage = new PipelineStage(type: "whatever")

  def config = [
    regions           : ["us-west-1"],
    credentials       : "fzlem",
    vpcId             : 'vpc1',
    securityGroupName : 'foo'
  ]

  def setup() {
    stage.context.putAll(config)
  }

  void "should force cache refresh server groups via oort when clusterName provided"() {
    setup:
    task.mort = Mock(MortService)

    when:
    task.execute(stage.asImmutable())

    then:
    1 * task.mort.forceCacheUpdate(DeleteSecurityGroupForceRefreshTask.REFRESH_TYPE, _) >> { String type, Map<String, ? extends Object> body ->
      assert body.securityGroupName == config.securityGroupName
      assert body.vpcId == config.vpcId
      assert body.account == config.credentials
      assert body.region == "us-west-1"
      assert body.evict == true
    }
  }
}

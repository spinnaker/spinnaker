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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForAllInstancesDownTaskSpec extends Specification {

  @Subject task = new WaitForAllInstancesDownTask()

  def mapper = new OrcaObjectMapper()

  void "should check cluster to get server groups"() {
    given:
    def pipeline = new Pipeline()
    task.objectMapper = mapper
    def response = mapper.writeValueAsString([
      name        : 'front50',
      serverGroups: [
        [
          region   : 'us-east-1',
          name     : 'front50-v000',
          asg      : [
            minSize: 1
          ],
          instances: [
            [
              health: [[state: 'Down']]
            ]
          ]
        ]
      ]
    ])

    task.oortService = Stub(OortService) {
      getCluster(*_) >> new Response('oort', 200, 'ok', [], new TypedString(response))
    }
    task.serverGroupCacheForceRefreshTask = Mock(ServerGroupCacheForceRefreshTask) {
      2 * execute(_) >> new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      0 * _
    }
    task.oortHelper = Mock(OortHelper) {
      1 * getTargetServerGroup("test", "front50-v000", "us-east-1", "aws") >> Optional.of(new TargetServerGroup(region: "us-east-1"))
      1 * getTargetServerGroup("test", "front50-v000", "us-east-1", "aws") >> Optional.empty()
      0 * _
    }

    and:
    def stage = new PipelineStage(pipeline, 'asgActionWaitForDownInstances', [
      'targetop.asg.disableAsg.name'   : 'front50-v000',
      'targetop.asg.disableAsg.regions': ['us-east-1'],
      'account.name'                   : 'test'
    ])

    expect:
    task.execute(stage).status == ExecutionStatus.SUCCEEDED

    when:
    task.execute(stage)

    then:
    IllegalStateException e = thrown()
    e.message.startsWith("Server group 'us-east-1:front50-v000' does not exist")
  }

  @Unroll
  void "should succeed as #hasSucceeded based on instance providers #healthProviderNames for instances #instances"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "")

    expect:
    hasSucceeded == task.hasSucceeded(stage, [minSize: 0], instances, healthProviderNames)

    where:
    hasSucceeded || healthProviderNames | instances
    true         || null | []
    true         || null | [[health: []]]
    true         || ['a'] | []
    true         || null | [[health: [[type: 'a', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]]]
    true         || ['a'] | [[health: [[type: 'a', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]]]
    true         || ['b'] | [[health: [[type: 'a', state: 'Down']]]]
    true         || ['b'] | [[health: [[type: 'a', state: 'Up']]]]
    true         || ['a'] | [[health: [[type: 'a', state: 'OutOfService']]]]
    true         || ['Amazon'] | [[health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown']]]]
    true         || ['GCE'] | [[health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown']]]]
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]]]

    // Multiple health providers.
    false        || null | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    true         || null | [[health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    true         || ['b'] | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    false        || ['b'] | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'Down']]]]
    true         || ['b'] | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'Down']]]]
    true         || ['a', 'b'] | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'Down']]]]
    true         || ['a', 'b'] | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'OutOfService']]]]
    true         || ['Amazon'] | [[health: [[type: 'a', state: 'Up'], [type: 'Amazon', healthClass: 'platform', state: 'Unknown']]]]
    true         || ['GCE'] | [[health: [[type: 'a', state: 'Up'], [type: 'GCE', healthClass: 'platform', state: 'Unknown']]]]
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'a', state: 'Up'], [type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]]]

    // Multiple instances.
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]
    true         || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]
    true         || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]
    true         || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]
    true         || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]
    true         || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]
    true         || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]
    true         || ['Amazon'] | [[health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown']]], [health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown']]]]
    true         || ['GCE'] | [[health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown']]], [health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown']]]]
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]], [health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]]]

    // Multiple instances with multiple health providers.
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]
    false        || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    false        || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    true         || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    true         || null | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    true         || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    true         || ['a'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]
    false        || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
    true         || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]
    true         || ['a', 'b'] | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]
    true         || ['Amazon'] | [[health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]], [health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]]]
    true         || ['GCE'] | [[health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]], [health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]]]
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]], [health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]]]

    // Ignoring health.
    true         || [] | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]
  }
}

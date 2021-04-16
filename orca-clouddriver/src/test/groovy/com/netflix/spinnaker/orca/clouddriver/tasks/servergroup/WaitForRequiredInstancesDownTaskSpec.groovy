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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WaitForRequiredInstancesDownTaskSpec extends Specification {

  CloudDriverService cloudDriverService = Mock()
  OortHelper oortHelper = Mock()
  ServerGroupCacheForceRefreshTask serverGroupCacheForceRefreshTask = Mock()

  @Subject
  WaitForRequiredInstancesDownTask task = new WaitForRequiredInstancesDownTask(
      cloudDriverService: cloudDriverService,
      oortHelper: oortHelper,
      serverGroupCacheForceRefreshTask: serverGroupCacheForceRefreshTask)


  def setup() {
    0 * _ // strict mocking
  }

  void "should fetch server groups"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("orca")
    def response = [
        region: 'us-east-1',
        name: 'front50-v000',
        asg: [
            minSize: 1
        ],
        instances: [
            [
                health: [[state: 'Down']]
            ]
        ]
    ]

    cloudDriverService.getServerGroup(*_) >> response

    2 * serverGroupCacheForceRefreshTask.execute(_) >> TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
    1 * oortHelper.getTargetServerGroup("test", "front50-v000", "us-east-1", "aws") >> Optional.of(new TargetServerGroup(region: "us-east-1"))
    1 * oortHelper.getTargetServerGroup("test", "front50-v000", "us-east-1", "aws") >> Optional.empty()

    and:
    def stage = new StageExecutionImpl(pipeline, 'asgActionWaitForDownInstances', [
        'targetop.asg.disableAsg.name': 'front50-v000',
        'targetop.asg.disableAsg.regions': ['us-east-1'],
        'account.name': 'test'
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
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [desiredPercentage: desiredPercentage])

    expect:
    hasSucceeded == task.hasSucceeded(stage, [capacity: [min: min, max: max, desired: desired], minSize: 0], instances, healthProviderNames)

    where:
    hasSucceeded || healthProviderNames   | instances                                                                                                                                                                                                                    | min              | max              | desired          | desiredPercentage
    true         || null                  | []                                                                                                                                                                                                                           | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: []]]                                                                                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | []                                                                                                                                                                                                                           | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: [[type: 'a', state: 'Down']]]]                                                                                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]]]                                                                                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]]]                                                                                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]]]                                                                                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    true         || ['b']                 | [[health: [[type: 'a', state: 'Down']]]]                                                                                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    true         || ['b']                 | [[health: [[type: 'a', state: 'Up']]]]                                                                                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | [[health: [[type: 'a', state: 'OutOfService']]]]                                                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    true         || ['Amazon']            | [[health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown']]]]                                                                                                                                                    | instances.size() | instances.size() | instances.size() | null
    true         || ['GCE']               | [[health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown']]]]                                                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]]]                                                                                                                                         | instances.size() | instances.size() | instances.size() | null

    // Multiple health providers.
    false        || null                  | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                                                         | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                                                           | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                                                           | instances.size() | instances.size() | instances.size() | null
    true         || ['b']                 | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                                                           | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                                                           | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                                                        | instances.size() | instances.size() | instances.size() | null
    false        || ['b']                 | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                                                        | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                                                        | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'Down']]]]                                                                                                                                                      | instances.size() | instances.size() | instances.size() | null
    true         || ['b']                 | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'Down']]]]                                                                                                                                                      | instances.size() | instances.size() | instances.size() | null
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'Down']]]]                                                                                                                                                      | instances.size() | instances.size() | instances.size() | null
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Unknown'], [type: 'b', state: 'OutOfService']]]]                                                                                                                                              | instances.size() | instances.size() | instances.size() | null
    true         || ['Amazon']            | [[health: [[type: 'a', state: 'Up'], [type: 'Amazon', healthClass: 'platform', state: 'Unknown']]]]                                                                                                                          | instances.size() | instances.size() | instances.size() | null
    true         || ['GCE']               | [[health: [[type: 'a', state: 'Up'], [type: 'GCE', healthClass: 'platform', state: 'Unknown']]]]                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'a', state: 'Up'], [type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]]]                                                                                                               | instances.size() | instances.size() | instances.size() | null

    // Multiple instances.
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | null
    true         || ['Amazon']            | [[health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown']]], [health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown']]]]                                                                           | instances.size() | instances.size() | instances.size() | null
    true         || ['GCE']               | [[health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown']]], [health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown']]]]                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]], [health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown']]]]                                                     | instances.size() | instances.size() | instances.size() | null

    // Multiple instances with multiple health providers.
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                  | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                   | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]                                                                                                                | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                   | instances.size() | instances.size() | instances.size() | null
    false        || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]                                                                                                              | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                  | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                   | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]                                                                                                                | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                   | instances.size() | instances.size() | instances.size() | null
    false        || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]                                                                                                              | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                       | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                  | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                   | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]                                                                                                                | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Up']]]]                                                                                                                     | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Down']]]]                                                                                                                   | instances.size() | instances.size() | instances.size() | null
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                | instances.size() | instances.size() | instances.size() | null
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Down']]]]                                                                                                                 | instances.size() | instances.size() | instances.size() | null
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down'], [type: 'b', state: 'Unknown']]]]                                                                                                              | instances.size() | instances.size() | instances.size() | null
    true         || ['Amazon']            | [[health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]], [health: [[type: 'Amazon', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]]]                       | instances.size() | instances.size() | instances.size() | null
    true         || ['GCE']               | [[health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]], [health: [[type: 'GCE', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]]]                             | instances.size() | instances.size() | instances.size() | null
    true         || ['SomeOtherPlatform'] | [[health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]], [health: [[type: 'SomeOtherPlatform', healthClass: 'platform', state: 'Unknown'], [type: 'a', state: 'Up']]]] | instances.size() | instances.size() | instances.size() | null

    // Ignoring health.
    true         || []                    | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up'], [type: 'b', state: 'Unknown']]]]                                                                                                                  | instances.size() | instances.size() | instances.size() | null

    // Desired percentage with multiple instances
    true         || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | 0
    true         || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | 40
    true         || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                 | instances.size() | instances.size() | instances.size() | 50
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | 10
    true         || null                  | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | 0
    false        || null                  | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | 90
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | 50
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | 10
    true         || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | 10
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | 100
    true         || ['a']                 | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | 0
    false        || ['a']                 | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | 100
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | 100
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | 50
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'a', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | 90
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Up']]]]                                                                                                                                                 | instances.size() | instances.size() | instances.size() | 0
    true         || ['a', 'b']            | [[health: [[type: 'a', state: 'Down']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                             | instances.size() | instances.size() | instances.size() | 0
    false        || ['a', 'b']            | [[health: [[type: 'a', state: 'Up']]], [health: [[type: 'b', state: 'Down']]]]                                                                                                                                               | instances.size() | instances.size() | instances.size() | 60
  }

  @Unroll
  void "should extract instance details from resultObjects in task"() {
    given:
    def stage = stage {
      context = [
          "kato.last.task.id": [
              id: lastTaskId
          ],
          "kato.tasks": [
              katoTask
          ]
      ]
    }

    and:
    def allInstances = [
        [name: "i-1234"],
        [name: "i-5678"]
    ]

    when:
    def instanceNamesToDisable = WaitForRequiredInstancesDownTask.getInstancesToDisable(stage, allInstances)*.name

    then:
    instanceNamesToDisable == expectedInstanceNamesToDisable

    where:
    lastTaskId | katoTask                                                                             || expectedInstanceNamesToDisable
    100        | [:]                                                                                  || []
    100        | [id: 100, resultObjects: [[:]]]                                                      || []
    100        | [id: 100, resultObjects: [[instanceIdsToDisable: ["i-1234"]]]]                       || ["i-1234"]
    100        | [id: 100, resultObjects: [[instanceIdsToDisable: ["i-1234", "i-5678"]],
                                           [discoverySkippedInstanceIds: ["i-1234"]]]]                || ["i-5678"]
    100        | [id: 100, resultObjects: [[instanceIdsToDisable: ["i-1234", "i-5678", "i-123456"]]]] || ["i-1234", "i-5678"]
  }
}

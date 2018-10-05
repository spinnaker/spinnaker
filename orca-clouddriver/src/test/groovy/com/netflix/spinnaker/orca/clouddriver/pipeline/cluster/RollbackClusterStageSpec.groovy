/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.cluster

import com.netflix.spinnaker.kork.dynamicconfig.SpringDynamicConfigService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class RollbackClusterStageSpec extends Specification {

  def trafficGuard = Mock(TrafficGuard)
  def env = new MockEnvironment()
  def lockingConfig = new LockingConfigurationProperties(new SpringDynamicConfigService(environment: env))

  @Subject
  def stageBuilder = new RollbackClusterStage(trafficGuard, lockingConfig)

  def setup() {
    env.setProperty('locking.enabled', 'true')
  }

  def "should not build any aroundStages()"() {
    expect:
    stageBuilder.aroundStages(stage {}).isEmpty()
  }

  def "should build rollback stages corresponding to each region with a rollback target"() {
    given:
    trafficGuard.hasDisableLock(MonikerHelper.friggaToMoniker('foo-main'), 'test', _) >> false
    def stage = stage {
      context = [
        credentials           : 'test',
        moniker               : [
          app: 'foo',
          cluster: 'foo-main',
          stack: 'main'],
        regions               : ["us-west-2", "us-east-1"],
        waitTimeBetweenRegions: 60,
      ]
      outputs = [
        rollbackTypes   : [
          "us-west-2": "EXPLICIT",
          "us-east-1": "PREVIOUS_IMAGE"
        ],
        rollbackContexts: [
          "us-west-2": ["foo": "bar"],
          "us-east-1": ["bar": "baz"]
        ]
      ]
    }

    when:
    def afterStages = buildAfterStages(stage)

    then:
    afterStages*.type == ["rollbackServerGroup", "wait", "rollbackServerGroup"]
    afterStages*.context.region == ["us-west-2", null, "us-east-1"]

    when: 'no wait between stages'
    stage.context.remove("waitTimeBetweenRegions")
    afterStages = buildAfterStages(stage)

    then:
    afterStages*.type == ["rollbackServerGroup", "rollbackServerGroup"]
    afterStages*.context.region == ["us-west-2", "us-east-1"]

    when: 'nothing to rollback in us-west-2'
    stage.outputs.rollbackTypes.remove("us-west-2")
    afterStages = buildAfterStages(stage)

    then:
    afterStages*.type == ["rollbackServerGroup"]
    afterStages*.context.region == ["us-east-1"]
  }

  private Iterable<Stage> buildAfterStages(Stage stage) {
    def graph = StageGraphBuilder.afterStages(stage)
    stageBuilder.afterStages(stage, graph)
    return graph.build()
  }

  @Unroll
  def "should propagate 'interestingHealthProviderNames' and 'sourceServerGroupCapacitySnapshot' to child rollback stage"() {
    given:
    def stage = (stageContext == null) ? null : stage { context = stageContext }

    expect:
    RollbackClusterStage.propagateParentStageContext(stage) == expectedPropagations

    where:
    stageContext                                                       || expectedPropagations
    null                                                               || [:]
    [:]                                                                || [:]
    [foo: "bar"]                                                       || [:]
    [interestingHealthProviderNames: null]                             || [interestingHealthProviderNames: null]            // do not care if value is null
    [interestingHealthProviderNames: ["Amazon"]]                       || [interestingHealthProviderNames: ["Amazon"]]
    [sourceServerGroupCapacitySnapshot: null]                          || [sourceServerGroupCapacitySnapshot: null]         // do not care if value is null
    [sourceServerGroupCapacitySnapshot: [min: 0, max: 10, desired: 5]] || [sourceServerGroupCapacitySnapshot: [min: 0, max: 10, desired: 5]]
  }

  def "should add locking stages to traffic guarded clusters"() {
    def stage = stage {
      context = [
        credentials           : 'test',
        moniker               : [
          app: 'foo',
          cluster: 'foo-main',
          stack: 'main'],
        regions               : ["us-west-2", "us-east-1"],
        waitTimeBetweenRegions: 60,
      ]
      outputs = [
        rollbackTypes   : [
          "us-west-2": "EXPLICIT",
          "us-east-1": "PREVIOUS_IMAGE"
        ],
        rollbackContexts: [
          "us-west-2": ["foo": "bar"],
          "us-east-1": ["bar": "baz"]
        ]
      ]
    }

    when:
    def afterStages = buildAfterStages(stage)

    then:
    1 * trafficGuard.hasDisableLock(MonikerHelper.friggaToMoniker('foo-main'), 'test', Location.region('us-east-1')) >> true
    1 * trafficGuard.hasDisableLock(MonikerHelper.friggaToMoniker('foo-main'), 'test', Location.region('us-west-2')) >> true
    afterStages*.type           == ["acquireLock", "rollbackServerGroup", "releaseLock", "wait", "acquireLock", "rollbackServerGroup", "releaseLock"]
    afterStages*.context.region == [ null        , "us-west-2"          ,  null        ,  null ,  null        , "us-east-1"          ,  null]
  }

  def "should only add locking stages to regions with traffic guards"() {
    def stage = stage {
      context = [
        credentials           : 'test',
        moniker               : [
          app: 'foo',
          cluster: 'foo-main',
          stack: 'main'],
        regions               : ["us-west-2", "us-east-1"],
        waitTimeBetweenRegions: 60,
      ]
      outputs = [
        rollbackTypes   : [
          "us-west-2": "EXPLICIT",
          "us-east-1": "PREVIOUS_IMAGE"
        ],
        rollbackContexts: [
          "us-west-2": ["foo": "bar"],
          "us-east-1": ["bar": "baz"]
        ]
      ]
    }

    when:
    def afterStages = buildAfterStages(stage)

    then:
    1 * trafficGuard.hasDisableLock(MonikerHelper.friggaToMoniker('foo-main'), 'test', Location.region('us-west-2')) >> true
    1 * trafficGuard.hasDisableLock(MonikerHelper.friggaToMoniker('foo-main'), 'test', Location.region('us-east-1')) >> false
    afterStages*.type           == ["acquireLock", "rollbackServerGroup", "releaseLock", "wait", "rollbackServerGroup"]
    afterStages*.context.region == [ null        , "us-west-2"          ,  null        ,  null , "us-east-1"]
  }
}

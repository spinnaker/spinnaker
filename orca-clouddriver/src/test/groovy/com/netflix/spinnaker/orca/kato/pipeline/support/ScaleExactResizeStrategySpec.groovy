/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import java.util.concurrent.atomic.AtomicInteger
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.Capacity
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.OptionalConfiguration
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class ScaleExactResizeStrategySpec extends Specification {

  OortHelper oortHelper = Mock(OortHelper)
  ScaleExactResizeStrategy strategy = new ScaleExactResizeStrategy(oortHelper: oortHelper)

  @Unroll
  def "should derive capacity from ASG (#current) when partial values supplied in context (#specifiedCap)"() {

    setup:
    def context = [
      capacity: specifiedCap
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "resizeServerGroup", context)

    when:
    def cap = strategy.capacityForOperation(stage, account, serverGroupName, cloudProvider, location, resizeConfig)

    then:
    cap.original == new Capacity(current)
    cap.target == expected
    1 * oortHelper.getTargetServerGroup(account, serverGroupName, region, cloudProvider) >> targetServerGroup
    0 * _

    where:
    specifiedCap                       | current                      || expected
    [min: 0]                           | [min: 1, max: 1, desired: 1] || new Capacity(min: 0, max: 1, desired: 1)
    [max: 0]                           | [min: 1, max: 1, desired: 1] || new Capacity(min: 0, max: 0, desired: 0)
    [max: 1]                           | [min: 1, max: 1, desired: 1] || new Capacity(min: 1, max: 1, desired: 1)
    [min: 2]                           | [min: 1, max: 1, desired: 1] || new Capacity(min: 2, max: 2, desired: 2)
    [min: 2]                           | [min: 1, max: 3, desired: 1] || new Capacity(min: 2, max: 3, desired: 2)
    [min: 0, max: 2]                   | [min: 1, max: 1, desired: 1] || new Capacity(min: 0, max: 2, desired: 1)
    [min: 0, max: 2]                   | [min: 1, max: 3, desired: 3] || new Capacity(min: 0, max: 2, desired: 2)
    [min: "0", max: "2"]               | [min: 1, max: 3, desired: 3] || new Capacity(min: 0, max: 2, desired: 2)
    [min: "0", max: "2", desired: "3"] | [min: 1, max: 3, desired: 3] || new Capacity(min: 0, max: 2, desired: 3)
    [:]                                | [min: 1, max: 3, desired: 3] || new Capacity(min: 1, max: 3, desired: 3)
    serverGroupName = asgName()
    targetServerGroup = Optional.of(new TargetServerGroup(name: serverGroupName, region: region, type: cloudProvider, capacity: current))
  }

  static final AtomicInteger asgSeq = new AtomicInteger(100)
  static final String cloudProvider = 'aws'
  static final String application = 'foo'
  static final String region = 'us-east-1'
  static final String account = 'test'
  static final String clusterName = application + '-main'
  static final Location location = new Location(type: Location.Type.REGION, value: region)
  static final OptionalConfiguration resizeConfig = new OptionalConfiguration(action: ResizeStrategy.ResizeAction.scale_exact)


  static String asgName() {
    clusterName + '-v' + asgSeq.incrementAndGet()
  }


}

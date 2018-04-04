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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import spock.lang.Specification
import spock.lang.Unroll

class ScaleRelativeResizeStrategySpec extends Specification {
  OortHelper oortHelper = Mock(OortHelper)
  Stage stage = ExecutionBuilder.stage {}
  ScaleRelativeResizeStrategy strategy = new ScaleRelativeResizeStrategy(oortHelper: oortHelper)

  @Unroll
  def "should scale target capacity up or down by percentage or number"() {

    when:
    def cap = strategy.capacityForOperation(stage, account, serverGroupName, cloudProvider, location, resizeConfig)

    then:
    cap.original == original
    cap.target == expected
    1 * oortHelper.getTargetServerGroup(account, serverGroupName, region, cloudProvider) >> targetServerGroup
    0 * _

    where:
    method     | direction    | value || want
    "scalePct" | null         | 50    || 15
    "scalePct" | "scale_up"   | 50    || 15
    "scalePct" | "scale_down" | 50    || 5
    "scalePct" | "scale_down" | 100   || 0
    "scalePct" | "scale_down" | 1000  || 0
    "scaleNum" | null         | 6     || 16
    "scaleNum" | "scale_up"   | 6     || 16
    "scaleNum" | "scale_down" | 6     || 4
    "scaleNum" | "scale_down" | 100   || 0
    serverGroupName = asgName()
    original = new ResizeStrategy.Capacity(10, 10, 10)
    expected = new ResizeStrategy.Capacity(want, want, want)
    targetServerGroup = Optional.of(
      new TargetServerGroup(
        name: serverGroupName,
        region: region,
        type: cloudProvider,
        capacity: [min: original.min, max: original.max, desired: original.desired]
      )
    )
    scalePct = method == 'scalePct' ? value : null
    scaleNum = method == 'scaleNum' ? value : null
    resizeConfig = cfg(direction, scalePct, scaleNum)
  }

  static final AtomicInteger asgSeq = new AtomicInteger(100)
  static final String cloudProvider = 'aws'
  static final String application = 'foo'
  static final String region = 'us-east-1'
  static final String account = 'test'
  static final String clusterName = application + '-main'
  static
  final Location location = new Location(type: Location.Type.REGION, value: region)

  ResizeStrategy.OptionalConfiguration cfg(String direction, Integer scalePct = null, Integer scaleNum = null) {
    def cfg = new ResizeStrategy.OptionalConfiguration()
    if (direction == "scale_down") {
      cfg.action = ResizeStrategy.ResizeAction.scale_down
    } else {
      cfg.action = ResizeStrategy.ResizeAction.scale_up
    }
    cfg.scalePct = scalePct
    cfg.scaleNum = scaleNum
    return cfg
  }

  static String asgName() {
    clusterName + '-v' + asgSeq.incrementAndGet()
  }

}

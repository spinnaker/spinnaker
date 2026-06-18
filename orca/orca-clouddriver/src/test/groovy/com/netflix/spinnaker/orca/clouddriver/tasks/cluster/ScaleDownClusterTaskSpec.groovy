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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import java.util.concurrent.atomic.AtomicInteger
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ScaleDownClusterTaskSpec extends Specification {

  @Subject
  ScaleDownClusterTask task = new ScaleDownClusterTask()

  @Unroll
  def 'extracts config from context'() {
    setup:
    def ctx = [:]
    if (remainingFullSizeServerGroups != null) {
      ctx.remainingFullSizeServerGroups = remainingFullSizeServerGroups
    }
    if (preferLargerOverNewer != null) {
      ctx.preferLargerOverNewer = preferLargerOverNewer
    }
    if (allowScaleDownActive != null) {
      ctx.allowScaleDownActive = allowScaleDownActive
    }
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "scaleDownCluster", ctx)

    when:
    def filtered = task.filterServerGroups(stage, account, location, serverGroups)

    then:
    filtered == expected

    where:

    remainingFullSizeServerGroups | preferLargerOverNewer | allowScaleDownActive | serverGroups                | expectedIdx
    null                          | null                  | null                 | [sg(), sg()]                | []
    1                             | null                  | null                 | [sg(), sg()]                | []
    1                             | false                 | false                | [sg(), sg()]                | []
    1                             | false                 | false                | [sg(), sg(true)]            | [1]
    1                             | false                 | true                 | [sg(), sg()]                | [0]
    1                             | false                 | false                | [sg(true), sg()]            | [0]
    1                             | true                  | false                | [sg(true, 10), sg(true, 9)] | [1]
    1                             | "true"                | false                | [sg(true, 10), sg(true, 9)] | [1]

    account = 'test'
    location = new Location(Location.Type.REGION, 'us-east-1')
    expected = expectedIdx.collect { serverGroups[it] }
  }

  def 'uses sequence number, not createdTime, to identify the newest server group'() {
    // Regression test: when a disabled VMSS shell is reused by a new deploy the
    // createdTime tag on the resource can be stale (from the original creation),
    // making the new server group appear older than the previous one.  Ordering
    // must be driven by sequence number (derived from the name) which is always
    // monotonically increasing and cannot be stale.
    given:
    def location = new Location(Location.Type.REGION, 'us-east-1')
    // v035 is the *newly deployed* group but has an older createdTime because
    // its VMSS shell was created during a previous (failed) pipeline run.
    def newGroup = new TargetServerGroup(
      name: 'app-stack-v035', region: 'us-east-1',
      createdTime: 1000L,   // stale — yesterday's timestamp
      disabled: true,
      instances: [[id: 'i1', healthState: 'Up']]
    )
    // v034 is the previous group and has a more recent createdTime (set today).
    def oldGroup = new TargetServerGroup(
      name: 'app-stack-v034', region: 'us-east-1',
      createdTime: 9999L,   // newer timestamp, but lower sequence
      disabled: true,
      instances: [[id: 'i2', healthState: 'Up']]
    )
    def stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline("orca"), "scaleDownCluster",
      [remainingFullSizeServerGroups: 1, allowScaleDownActive: true]
    )

    when:
    def result = task.filterServerGroups(stage, 'test', location, [newGroup, oldGroup])

    then: 'the lower-sequence (older) group is selected for scale-down, not the higher-sequence new one'
    result == [oldGroup]
  }

  static final AtomicInteger inc = new AtomicInteger()

  TargetServerGroup sg(boolean disabled = false, int instances = 10) {
    new TargetServerGroup(name: 'foo-v' + inc.incrementAndGet(), region: 'us-east-1', createdTime: inc.incrementAndGet(), disabled: disabled, instances: (0..instances).collect {
      [id: 'i' + inc.incrementAndGet(), healthState: (disabled ? 'OutOfService' : 'Up')]
    })
  }
}

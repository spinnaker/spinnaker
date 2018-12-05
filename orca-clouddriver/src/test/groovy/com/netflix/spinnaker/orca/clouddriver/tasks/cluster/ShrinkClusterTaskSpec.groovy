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

import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard

import java.util.concurrent.atomic.AtomicInteger
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask.CompositeComparator
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask.CreatedTime
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask.InstanceCount
import com.netflix.spinnaker.orca.clouddriver.tasks.cluster.AbstractClusterWideClouddriverTask.IsActive
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ShrinkClusterTaskSpec extends Specification {

  static AtomicInteger inc = new AtomicInteger(100)
  OortService oortService = Mock(OortService)
  KatoService katoService = Mock(KatoService)
  OortHelper oortHelper = Mock(OortHelper)
  TrafficGuard trafficGuard = Mock(TrafficGuard)

  @Subject
  ShrinkClusterTask task

  def setup() {
    task = new ShrinkClusterTask(oortHelper: oortHelper, katoService: katoService, trafficGuard: trafficGuard)
  }

  @Unroll
  def "stage context #desc"() {
    setup:
    Stage orchestration = new Stage(Execution.newOrchestration("orca"), 'test', context)
    Map cluster = [serverGroups: serverGroups]

    when:
    def result = task.execute(orchestration)

    then:
    1 * oortHelper.getCluster('foo', 'test', 'foo-test', 'aws') >> cluster

    (expectedItems ? 1 : 0) * katoService.requestOperations('aws', _) >> { p, ops ->
      assert ops.size == expected.size()
      expected.each { expect ->
        assert ops.find { it.destroyServerGroup.serverGroupName == expect.name && it.destroyServerGroup.region == expect.region }
      }
      rx.Observable.just(new TaskId('1'))
    }

    where:
    allowDeleteActive | shrinkToSize | retainLargerOverNewer | serverGroups                                                             | expectedItems | desc
    null              | 1            | true                  | [mkSG(), mkSG()]                                                         | []            | 'default allowDeleteActive false'
    false             | null         | true                  | [mkSG(health: 'Down'), mkSG(health: 'Down'), mkSG()]                     | [0, 1]        | 'default shrinkToSize 1'
    false             | 1            | null                  | [mkSG(health: 'Down', instances: 5), mkSG(health: 'Down', instances: 4)] | [0]           | 'default retainLargerOverNewer false'
    false             | "2"          | false                 | [mkSG(health: 'Down'), mkSG(health: 'Down'), mkSG(health: 'Down')]       | [0]           | 'handles shrinkToSize as a String'
    false             | 2.0          | false                 | [mkSG(health: 'Down'), mkSG(health: 'Down'), mkSG(health: 'Down')]       | [0]           | 'handles shrinkToSize as a Double'
    "true"            | 1            | false                 | [mkSG(), mkSG()]                                                         | [0]           | 'handles allowDeleteActive as String'
    true              | 1            | "true"                | [mkSG(instances: 5), mkSG(instances: 4)]                                 | [1]           | 'handles largerOverNewer as String'


    context = mkCtx(allowDeleteActive, shrinkToSize, retainLargerOverNewer)
    expected = expectedItems.collect { serverGroups[it] }
  }

  @Unroll
  def "deletion priority #desc"() {
    setup:
    def context = [
      allowDeleteActive: allowDeleteActive,
      shrinkToSize: shrinkToSize,
      retainLargerOverNewer: retainLargerOverNewer
    ]
    def stage = new Stage(Execution.newPipeline("orca"), 'shrinkCluster', context)

    when:
    def toDelete = task.filterServerGroups(stage, account, location, serverGroups)

    then:
    toDelete == expected

    where:
    //test data generation is a bit dirty - serverGroups is the data set, expectedItems is the indexes in that set in deletion
    //order
    //each invocation of mkSG creates a 'newer' ServerGroup than any previous invocation
    allowDeleteActive | shrinkToSize | retainLargerOverNewer | serverGroups                                                       | expectedItems | desc
    true              | 1            | true                  | [mkTSG(), mkTSG(instances: 11), mkTSG(instances: 9)]               | [0, 2]        | 'prefers size over age when retainLargerOverNewer'
    true              | 1            | false                 | [mkTSG(), mkTSG(instances: 11), mkTSG(instances: 9)]               | [1, 0]        | 'prefers age over size when not retainLargerOverNewer'
    false             | 1            | true                  | [mkTSG(), mkTSG(instances: 11), mkTSG(instances: 9)]               | []            | 'dont delete any active if allowDeleteActive is false'
    false             | 1            | true                  | [mkTSG(health: 'Down'), mkTSG(instances: 11), mkTSG(instances: 9)] | [0]           | 'if at least shrinkToSize active groups are retained, dont delete'
    true              | 1            | true                  | [mkTSG(), mkTSG(), mkTSG()]                                        | [1, 0]        | 'falls through to createdTime if size is equal'
    true              | 2            | false                 | [mkTSG(), mkTSG(), mkTSG()]                                        | [0]           | 'keeps shrinkToSize items'
    true              | 4            | true                  | [mkTSG(), mkTSG(), mkTSG()]                                        | []            | 'deletes nothing if less serverGroups than shrinkToSize'

    expected = expectedItems.collect { serverGroups[it] }
    account = 'test'
    location = new Location(Location.Type.REGION, 'us-east-1')
    cloudProvider = 'aws'

  }

  def 'instanceCount prefers larger'() {
    def larger = mkTSG(instances: 10)
    def smaller = mkTSG(instances: 9)

    expect:
    [smaller, larger].sort(true, new InstanceCount()) == [larger, smaller]
  }

  def 'createdTime prefers newer'() {
    def older = mkTSG()
    def newer = mkTSG()

    expect:
    [older, newer].sort(true, new CreatedTime()) == [newer, older]
  }

  def 'isActive prefers active'() {
    def inactive = mkTSG(health: 'OutOfService')
    def active = mkTSG()

    expect:
    [inactive, active].sort(true, new IsActive()) == [active, inactive]
  }

  def 'empty instances is inactive'() {
    def inactive = mkTSG(instances: 0)
    def active = mkTSG()

    expect:
    [inactive, active].sort(true, new IsActive()) == [active, inactive]
  }

  def 'compositeComparator applies in order'() {
    def olderButBigger = mkTSG(instances: 10)
    def newerButSmaller = mkTSG(instances: 5)
    def items = [olderButBigger, newerButSmaller]

    expect:
    items.sort(false, new CompositeComparator([new CreatedTime(), new InstanceCount()])) == [newerButSmaller, olderButBigger]
    items.sort(false, new CompositeComparator([new InstanceCount(), new CreatedTime()])) == [olderButBigger, newerButSmaller]
  }


  private Map<String, Object> mkCtx(Object allowDeleteActive, Object shrinkToSize, Object retainLargerOverNewer) {
    [
      cluster              : 'foo-test',
      credentials          : 'test',
      regions              : ['us-east-1', 'us-west-1'],
      allowDeleteActive    : allowDeleteActive,
      shrinkToSize         : shrinkToSize,
      retainLargerOverNewer: retainLargerOverNewer

    ]
  }

  private TargetServerGroup mkTSG(Map params = [:]) {
    return new TargetServerGroup(mkSG(params))
  }

  private Map<String, Object> mkSG(Map params = [:]) {
    int instances = params.instances == null ? 10 : params.instances
    int ctr = inc.incrementAndGet()
    [
      name       : "foo-test-v$ctr".toString(),
      region     : params.region ?: 'us-east-1',
      createdTime: ctr.longValue(),
      instances  : instances ? (1..instances).collect {
        [
          instanceId : "i-$it".toString(),
          healthState: params.health ?: 'Up'
        ]
      } : [],
    ]
  }

}

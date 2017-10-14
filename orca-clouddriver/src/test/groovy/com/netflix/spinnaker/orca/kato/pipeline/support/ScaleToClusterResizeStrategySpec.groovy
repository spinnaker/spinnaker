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

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy.Capacity
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.atomic.AtomicInteger

class ScaleToClusterResizeStrategySpec extends Specification {


  OortHelper oortHelper = Mock(OortHelper)
  @Subject ScaleToClusterResizeStrategy strategy = new ScaleToClusterResizeStrategy(oortHelper: oortHelper)

  def 'empty or missing cluster fails on scale_to_cluster'() {
    given:
    Stage stage = new Stage(null, "stage", [moniker: [app: application, cluster: clusterName]])

    when:
    strategy.capacityForOperation(stage, account, serverGroupName, cloudProvider, location, resizeConfig)

    then:
    thrown(IllegalStateException)
    1 * oortHelper.getCluster(application, account, clusterName, cloudProvider) >> cluster
    0 * _

    where:
    serverGroupName = asgName()
    cluster << [Optional.empty(), Optional.of([serverGroups: []]), Optional.of([serverGroups: [mkSG(0, 0, 0, 'boom')]])]
    resizeConfig = cfg()
  }

  def 'capacity is the maximum value of min/max/desired across the cluster for scale_to_cluster'() {
    given:
    Stage stage = new Stage(null, "stage", [moniker: [app: application, cluster: clusterName]])

    when:
    def cap = strategy.capacityForOperation(stage, account, serverGroupName, cloudProvider, location, resizeConfig)

    then:
    cap == expectedCapacity
    1 * oortHelper.getCluster(application, account, clusterName, cloudProvider) >> cluster
    0 * _

    where:
    serverGroups                                          | expectedCapacity
    [mkSG()]                                              | new Capacity(0, 0, 0)
    [mkSG(1)]                                             | new Capacity(1, 1, 1)
    [mkSG(1), mkSG(1000, 1000, 1000, 'different-region')] | new Capacity(1, 1, 1)
    [mkSG(0, 0, 10), mkSG(0, 1, 5), mkSG(5, 0, 9)]        | new Capacity(10, 5, 1)

    serverGroupName = asgName()
    resizeConfig = cfg()
    cluster = Optional.of([serverGroups: serverGroups])
  }

  def 'desired capacity is increased by scalePct or scaleNum for scale_to_cluster within the min/max bounds'() {
    given:
    Stage stage = new Stage(null, "stage", [moniker: [app: application, cluster: clusterName]])

    when:
    def cap = strategy.capacityForOperation(stage, account, serverGroupName, cloudProvider, location, resizeConfig)

    then:
    cap == expectedCapacity
    1 * oortHelper.getCluster(application, account, clusterName, cloudProvider) >> cluster
    0 * _

    where:
    serverGroups                                   | scaleNum | scalePct | expectedCapacity
    [mkSG()]                                       | 1        | null     | new Capacity(0, 0, 0)
    [mkSG(1)]                                      | 1        | null     | new Capacity(1, 1, 1)
    [mkSG(0, 0, 10), mkSG(0, 1, 5), mkSG(5, 0, 9)] | 1        | null     | new Capacity(10, 6, 1)
    [mkSG(100, 0, 1000)]                           | null     | 1        | new Capacity(1000, 101, 0)

    resizeConfig = cfg(scalePct, scaleNum)
    cluster = Optional.of([serverGroups: serverGroups])
    serverGroupName = asgName()
  }

  static final AtomicInteger asgSeq = new AtomicInteger(100)
  static final String cloudProvider = 'aws'
  static final String application = 'foo'
  static final String region = 'us-east-1'
  static final String account = 'test'
  static final String clusterName = application + '-main'
  static final Location location = new Location(type: Location.Type.REGION, value: region)

  static String asgName() {
    clusterName + '-v' + asgSeq.incrementAndGet()
  }

  ResizeStrategy.OptionalConfiguration cfg(Integer scalePct = null, Integer scaleNum = null) {
    def cfg = new ResizeStrategy.OptionalConfiguration()
    cfg.action = ResizeStrategy.ResizeAction.scale_to_cluster
    cfg.scalePct = scalePct
    cfg.scaleNum = scaleNum
    return cfg
  }

  Map mkSG(int desired = 0, Integer min = null, Integer max = null, String regionName = region) {
    Capacity capacity = new Capacity(max == null ? desired : max, desired, min == null ? desired : min)
    [
      type    : cloudProvider,
      region  : regionName,
      name    : asgName(),
      capacity: capacity
    ]
  }
}

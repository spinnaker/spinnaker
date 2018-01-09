/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"): ,
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

package com.netflix.spinnaker.clouddriver.ecs.cache

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsTargetGroupCacheClient
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsTargetGroup
import spock.lang.Specification
import spock.lang.Subject

class EcsTargetGroupCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
                          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  EcsTargetGroupCacheClient client = new EcsTargetGroupCacheClient(cacheView, objectMapper)

  def 'should convert cache data into object'() {
    given:
    def loadBalancerName = 'test-name'
    def account = 'test-account'
    def region = 'us-west-1'
    def vpcId = 'vpc-id'
    def loadBalancerType = 'classic'
    def targetGroupName = 'test-target-group'

    def loadbalancerKey = Keys.getLoadBalancerKey(loadBalancerName, account, region, vpcId, loadBalancerType)
    def targetGroupKey = Keys.getTargetGroupKey(targetGroupName, account, region, vpcId)

    def givenEcsTargetGroup = new EcsTargetGroup(
      loadBalancerNames: [loadBalancerName],
      instances: [],
      healthCheckTimeoutSeconds: 30,
      targetGroupArn: 'arn',
      healthCheckPort: 1337,
      matcher: [:],
      healthCheckProtocol: 'http',
      targetGroupName: targetGroupName,
      healthCheckPath: '/',
      protocol: 'http',
      port: 1337,
      healthCheckIntervalSeconds: 30,
      healthyThresholdCount: 5,
      vpcId: vpcId,
      unhealthyThresholdCount: 5,
      attributes: [:],
    )

    def attributes = objectMapper.convertValue(givenEcsTargetGroup, Map)
    def relations = [loadBalancers: [loadbalancerKey]]

    when:
    def retrievedLoadbalancers = client.findAll()

    then:
    cacheView.filterIdentifiers(_, _) >> Collections.singleton(targetGroupKey)
    cacheView.getAll(_, _, _) >> Collections.singleton(new DefaultCacheData(targetGroupKey, attributes, relations))
    retrievedLoadbalancers.size() == 1
    retrievedLoadbalancers.get(0) == givenEcsTargetGroup
  }
}

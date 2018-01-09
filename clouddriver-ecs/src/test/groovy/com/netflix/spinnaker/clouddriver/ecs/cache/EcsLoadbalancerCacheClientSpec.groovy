/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"):
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
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache
import spock.lang.Specification
import spock.lang.Subject

class EcsLoadbalancerCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
                          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  EcsLoadbalancerCacheClient client = new EcsLoadbalancerCacheClient(cacheView, objectMapper)

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

    def givenEcsLoadbalancer = new EcsLoadBalancerCache(
      account: account,
      region: region,
      loadBalancerArn: 'arn',
      loadBalancerType: loadBalancerType,
      cloudProvider: EcsCloudProvider.ID,
      listeners: [],
      scheme: 'scheme',
      availabilityZones: [],
      ipAddressType: 'ipv4',
      loadBalancerName: loadBalancerName,
      canonicalHostedZoneId: 'zone-id',
      vpcId: vpcId,
      dnsname: 'dns-name',
      createdTime: System.currentTimeMillis(),
      subnets: [],
      securityGroups: [],
      targetGroups: [targetGroupName],
      serverGroups: []
    )

    def attributes = objectMapper.convertValue(givenEcsLoadbalancer, Map)
    def relations = [targetGroups: [targetGroupKey]]

    when:
    def retrievedLoadbalancers = client.findAll()

    then:
    cacheView.filterIdentifiers(_, _) >> Collections.singleton(loadbalancerKey)
    cacheView.getAll(_, _, _) >> Collections.singleton(new DefaultCacheData(loadbalancerKey, attributes, relations))
    retrievedLoadbalancers.size() == 1
    retrievedLoadbalancers.get(0) == givenEcsLoadbalancer
  }
}

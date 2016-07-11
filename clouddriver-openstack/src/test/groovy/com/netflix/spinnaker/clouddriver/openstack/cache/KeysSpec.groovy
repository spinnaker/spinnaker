/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.cache

import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS

@Unroll
class KeysSpec extends Specification {

  void "test parse key format - #testCase"() {
    when:
    Map<String, String> result = Keys.parse(value)

    then:
    result == expected

    where:
    testCase            | value         | expected
    'no delimiter'      | 'test'        | null
    'less than 5 parts' | 'test:test'   | null
    'more than 5 parts' | 't:t:t:t:t:t' | null
  }

  void "test invalid parts - #testCase"() {
    when:
    Map<String, String> result = Keys.parse(value)

    then:
    result == expected

    where:
    testCase    | value               | expected
    'provider'  | 'openstackprovider' | null
    'namespace' | 'stuff'             | null
  }

  void "test instance map"() {
    given:
    String instanceId = 'testInstance'
    String account = 'testAccount'
    String region = 'testRegion'
    String key = Keys.getInstanceKey(instanceId, account, region)

    when:
    Map<String, String> result = Keys.parse(key)

    then:
    result == [account: account, region: region, instanceId: instanceId, provider: ID, type: INSTANCES.ns]
  }

  void "test application map"() {
    given:
    String application = 'application'
    String key = Keys.getApplicationKey(application)

    when:
    Map<String, String> result = Keys.parse(key)

    then:
    result == [application: application, provider: ID, type: APPLICATIONS.ns]
  }

  void "test cluster map"() {
    given:
    String application = 'myapp'
    String stack = 'stack'
    String detail = 'detail'
    String cluster = "$application-$stack-$detail-v000"
    String account = 'account'
    String key = Keys.getClusterKey(account, application, cluster)

    when:
    Map<String, String> result = Keys.parse(key)

    then:
    result == [application: application, account: account, cluster: cluster, stack: stack, detail: detail, provider: ID, type: CLUSTERS.ns]
  }

  void "test subnet map"() {
    given:
    String subnetId = UUID.randomUUID().toString()
    String region = 'region'
    String account = 'account'
    String subnetKey = Keys.getSubnetKey(subnetId, region, account)

    when:
    Map<String, String> result = Keys.parse(subnetKey)

    then:
    result == [region: region, id: subnetId, account: account, provider: ID, type: SUBNETS.ns]
  }

  void "test get instance key"() {
    given:
    String instanceId = UUID.randomUUID().toString()
    String account = 'account'
    String region = 'region'

    when:
    String result = Keys.getInstanceKey(instanceId, account, region)

    then:
    result == "${ID}:${INSTANCES}:${account}:${region}:${instanceId}" as String
  }

  void "test get application key"() {
    given:
    String application = 'application'

    when:
    String result = Keys.getApplicationKey(application)

    then:
    result == "${ID}:${APPLICATIONS}:${application}" as String
  }

  void "test get server group key"() {
    given:
    String cluster = 'myapp-teststack'
    String serverGroupName = "$cluster-v000"
    String account = 'account'
    String region = 'region'

    when:
    String result = Keys.getServerGroupKey(serverGroupName, account, region)

    then:
    result == "${ID}:${SERVER_GROUPS}:${cluster}:${account}:${region}:${serverGroupName}" as String
  }

  void "test get cluster key"() {
    given:
    String application = 'myapp'
    String cluster = 'cluster'
    String account = 'account'

    when:
    String result = Keys.getClusterKey(account, application, cluster)

    then:
    result == "${ID}:${CLUSTERS}:${account}:${application}:${cluster}" as String
  }

  void "test get subnet key"() {
    given:
    String subnetId = UUID.randomUUID().toString()
    String region = 'region'
    String account = 'account'

    when:
    String result = Keys.getSubnetKey(subnetId, region, account)

    then:
    result == "${ID}:${SUBNETS}:${subnetId}:${account}:${region}" as String
  }

  def "test get security group key"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'name'
    def region = 'region'
    def account = 'account'

    when:
    def result = Keys.getSecurityGroupKey(name, id, account, region)

    then:
    result == "${ID}:${SECURITY_GROUPS}:${name}:${id}:${region}:${account}" as String
  }

  def "test security group map"() {
    given:
    def id = UUID.randomUUID().toString()
    def name = 'name'
    def region = 'region'
    def account = 'account'
    def key = Keys.getSecurityGroupKey(name, id, account, region)

    when:
    Map<String, String> result = Keys.parse(key)

    then:
    result == [application: name, account: account, region: region, id: id, name: name, provider: ID, type: SECURITY_GROUPS.ns]
  }
}

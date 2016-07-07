/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cf.cache

import spock.lang.Specification
import spock.lang.Unroll

class KeysSpec extends Specification {

  @Unroll
  def "namespace string generation"(Keys.Namespace ns, String expected) {
    expect:
    ns.toString() == expected

    where:
    ns                              | expected
    Keys.Namespace.SERVER_GROUPS    | 'serverGroups'
  }

  def 'key parsing'() {
    expect:
    Keys.parse(Keys.getInstanceKey('myapp-production-v000/0', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.INSTANCES.ns, account: 'account', region: 'region', instanceId: 'myapp-production-v000/0']
    Keys.parse(Keys.getServerGroupKey('theAsg', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg', serverGroup: 'theAsg', account: 'account', region: 'region', detail: null, stack: null, sequence: null]
    Keys.parse(Keys.getServerGroupKey('theAsg-v001', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg', serverGroup: 'theAsg-v001', account: 'account', region: 'region', detail: null, stack: null, sequence: '1']
    Keys.parse(Keys.getServerGroupKey('theAsg-test-v001', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg-test', serverGroup: 'theAsg-test-v001', account: 'account', region: 'region', stack: 'test', detail: null, sequence: '1']
    Keys.parse(Keys.getServerGroupKey('theAsg--details-v001', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg--details', serverGroup: 'theAsg--details-v001', account: 'account', region: 'region', stack: null, detail: 'details', sequence: '1']
    Keys.parse(Keys.getClusterKey('cluster', 'application', 'account')) == [provider: 'cf', type: Keys.Namespace.CLUSTERS.ns, cluster: 'cluster', application: 'application', account: 'account', stack: null, detail: null]
    Keys.parse(Keys.getClusterKey('cluster-test', 'application', 'account')) == [provider: 'cf', type: Keys.Namespace.CLUSTERS.ns, cluster: 'cluster-test', application: 'application', account: 'account', stack: 'test', detail: null]
    Keys.parse(Keys.getClusterKey('cluster-test-useast1', 'application', 'account')) == [provider: 'cf', type: Keys.Namespace.CLUSTERS.ns, cluster: 'cluster-test-useast1', application: 'application', account: 'account', stack: 'test', detail: 'useast1']
    Keys.parse(Keys.getApplicationKey('theApp')) == [provider: 'cf', type: Keys.Namespace.APPLICATIONS.ns, application: 'theapp']
    Keys.parse(Keys.getLoadBalancerKey('loadBalancer', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.LOAD_BALANCERS.ns, loadBalancer: 'loadBalancer', account: 'account', region: 'region', application: 'loadbalancer', stack: null, detail: null]
    Keys.parse(Keys.getLoadBalancerKey('kato-main-frontend', 'account', 'region')) == [provider: 'cf', type: Keys.Namespace.LOAD_BALANCERS.ns, loadBalancer: 'kato-main-frontend', account: 'account', region: 'region', stack: 'main', detail: 'frontend', application: 'kato']
  }

  def "don't expect to parse bad keys"() {
    when:
    def results = Keys.parse('bad-key')

    then:
    results == null

    when:
    results = Keys.parse('cf:ns-i-dont-know')

    then:
    results == null
  }

  def "short circuit on non-provider key"() {
    expect:
    Keys.parse("gce:instances:account:region:myapp-production-v000/0") == null
    Keys.parse("aws:instances:account:region:myapp-production-v000/0") == null
  }

}

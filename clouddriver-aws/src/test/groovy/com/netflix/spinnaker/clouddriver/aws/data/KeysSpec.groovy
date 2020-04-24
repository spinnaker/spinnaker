/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.data

import spock.lang.Specification
import com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace
import spock.lang.Unroll

class KeysSpec extends Specification {

  @Unroll
  def 'namespace string generation'(Namespace ns, String expected) {
    expect:
    ns.toString() == expected

    where:
    ns                       | expected
    Namespace.APPLICATIONS   | "applications"
    Namespace.LAUNCH_CONFIGS | "launchConfigs"
  }

  def 'key parsing'() {
    expect:
    Keys.parse(Keys.getApplicationKey('theApp')) == [provider: 'aws', type: Namespace.APPLICATIONS.ns, application: 'theapp']
    Keys.parse(Keys.getServerGroupKey('theAsg', 'account', 'region')) == [provider: 'aws', type: Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg', serverGroup: 'theAsg', account: 'account', region: 'region', detail: null, stack: null, sequence: null]
    Keys.parse(Keys.getServerGroupKey('theAsg-v001', 'account', 'region')) == [provider: 'aws', type: Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg', serverGroup: 'theAsg-v001', account: 'account', region: 'region', detail: null, stack: null, sequence: '1']
    Keys.parse(Keys.getServerGroupKey('theAsg-test-v001', 'account', 'region')) == [provider: 'aws', type: Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg-test', serverGroup: 'theAsg-test-v001', account: 'account', region: 'region', stack: 'test', detail: null, sequence: '1']
    Keys.parse(Keys.getServerGroupKey('theAsg--details-v001', 'account', 'region')) == [provider: 'aws', type: Namespace.SERVER_GROUPS.ns, application: 'theasg', cluster: 'theAsg--details', serverGroup: 'theAsg--details-v001', account: 'account', region: 'region', stack: null, detail: 'details', sequence: '1']
    Keys.parse(Keys.getClusterKey('cluster', 'application', 'account')) == [provider: 'aws', type: Namespace.CLUSTERS.ns, cluster: 'cluster', application: 'application', account: 'account', stack: null, detail: null]
    Keys.parse(Keys.getClusterKey('cluster-test', 'application', 'account')) == [provider: 'aws', type: Namespace.CLUSTERS.ns, cluster: 'cluster-test', application: 'application', account: 'account', stack: 'test', detail: null]
    Keys.parse(Keys.getClusterKey('cluster-test-useast1', 'application', 'account')) == [provider: 'aws', type: Namespace.CLUSTERS.ns, cluster: 'cluster-test-useast1', application: 'application', account: 'account', stack: 'test', detail: 'useast1']
    Keys.parse(Keys.getImageKey('image', 'account', 'region')) == [provider: 'aws', type: Namespace.IMAGES.ns, imageId: 'image', region: 'region', account: 'account']
    Keys.parse(Keys.getInstanceHealthKey('instanceId', 'account', 'region', 'provider')) == [provider: 'aws', type: Namespace.HEALTH.ns, instanceId: 'instanceId', account: 'account', region: 'region', provider: 'provider']
    Keys.parse(Keys.getLaunchConfigKey('kato-main-v056-10062014221307', 'account', 'region')) == [provider: 'aws', type: Namespace.LAUNCH_CONFIGS.ns, launchConfig: 'kato-main-v056-10062014221307', region: 'region', account: 'account', application: 'kato', stack: 'main']
    Keys.parse(Keys.getLoadBalancerKey('loadBalancer', 'account', 'region', 'vpc-12345', 'classic')) == [provider: 'aws', type: Namespace.LOAD_BALANCERS.ns, loadBalancer: 'loadBalancer', account: 'account', region: 'region', vpcId: 'vpc-12345', loadBalancerType: 'classic', application: 'loadbalancer', stack: null, detail: null]
    Keys.parse(Keys.getLoadBalancerKey('kato-main-frontend', 'account', 'region', null, 'classic')) == [provider: 'aws', type: Namespace.LOAD_BALANCERS.ns, loadBalancer: 'kato-main-frontend', account: 'account', region: 'region', vpcId: null, loadBalancerType: 'classic', stack: 'main', detail: 'frontend', application: 'kato']
    Keys.parse(Keys.getLoadBalancerKey('kato-main-frontend', 'account', 'region', null, null)) == [provider: 'aws', type: Namespace.LOAD_BALANCERS.ns, loadBalancer: 'kato-main-frontend', account: 'account', region: 'region', vpcId: null, loadBalancerType: 'classic', stack: 'main', detail: 'frontend', application: 'kato']
    Keys.parse(Keys.getLoadBalancerKey('loadBalancer', 'account', 'region', 'vpc-12345', 'application')) == [provider: 'aws', type: Namespace.LOAD_BALANCERS.ns, loadBalancer: 'loadBalancer', account: 'account', region: 'region', vpcId: 'vpc-12345', loadBalancerType: 'application', application: 'loadbalancer', stack: null, detail: null]
    Keys.parse(Keys.getLaunchTemplateKey('kato-main-v056-10062014221307', 'account', 'region')) == [provider: 'aws', type: Namespace.LAUNCH_TEMPLATES.ns, launchTemplateName: 'kato-main-v056-10062014221307', region: 'region', account: 'account', application: 'kato', stack: 'main']
  }

  def 'load balancer key backwards compatibility'() {
    expect:
    Keys.getLoadBalancerKey('lbname', 'account', 'region', null, 'classic') == 'aws:loadBalancers:account:region:lbname'
    Keys.getLoadBalancerKey('lbname', 'account', 'region', null, null) == 'aws:loadBalancers:account:region:lbname'
    Keys.getLoadBalancerKey('lbname', 'account', 'region', 'vpc', 'classic') == 'aws:loadBalancers:account:region:lbname:vpc'
    Keys.getLoadBalancerKey('lbname', 'account', 'region', 'vpc', null) == 'aws:loadBalancers:account:region:lbname:vpc'

    Keys.getLoadBalancerKey('lbname', 'account', 'region', 'vpc', 'application') == 'aws:loadBalancers:account:region:lbname:vpc:application'

    Keys.parse('aws:loadBalancers:account:region:lbname') == [provider: 'aws', type: 'loadBalancers', loadBalancer: 'lbname', account: 'account', region: 'region', vpcId: null, loadBalancerType: 'classic', stack: null, detail: null, application: 'lbname']
    Keys.parse('aws:loadBalancers:account:region:lbname:vpc') == [provider: 'aws', type: 'loadBalancers', loadBalancer: 'lbname', account: 'account', region: 'region', vpcId: 'vpc', loadBalancerType: 'classic', stack: null, detail: null, application: 'lbname']
    Keys.parse('aws:loadBalancers:account:region:lbname:vpc:application') == [provider: 'aws', type: 'loadBalancers', loadBalancer: 'lbname', account: 'account', region: 'region', vpcId: 'vpc', loadBalancerType: 'application', stack: null, detail: null, application: 'lbname']
  }

  def 'typed loadbalancers require a vpcId'() {
    //both will ignore the loadBalancerType of the LHS because there is no vpcId so it must be 'classic' type
    Keys.getLoadBalancerKey('lbname', 'account', 'region', null, 'application') == 'aws:loadBalancers:account:region:lbname'
    Keys.parse('aws:loadBalancers:account:region:lbname::application') == [provider: 'aws', type: 'loadBalancers', loadBalancer: 'lbname', account: 'account', region: 'region', vpcId: null, loadBalancerType: 'classic', stack: null, detail: null, application: 'lbname']

  }

  @Unroll
  def 'key fields match namespace fields if present'() {

    expect:
    Keys.parse(key).keySet() == namespace.fields

    where:

    key                                                                                                       | namespace
    "aws:serverGroups:appname:appname-stack-detail:test:us-west-1:appname-stack-detail-v000:stack:detail:000" | Namespace.SERVER_GROUPS
    "aws:instances:test:us-west-1:i-abc123abc123"                                                             | Namespace.INSTANCES
    "aws:loadBalancers:test:us-west-1:appname-stack-vpc0:vpc-abc123:appname:stack:detail:classic"             | Namespace.LOAD_BALANCERS
    "aws:clusters:appname:test:appname-stack-detail:stack:detail:"                                            | Namespace.CLUSTERS
  }
}

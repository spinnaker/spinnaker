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

package com.netflix.spinnaker.oort.data.aws

import spock.lang.Specification
import spock.lang.Unroll

class KeysSpec extends Specification {

  @Unroll
  def 'namespace string generation'(Keys.Namespace ns, String expected) {
    expect:
    ns.toString() == expected

    where:
    ns                                    | expected
    Keys.Namespace.APPLICATIONS           | "applications"
    Keys.Namespace.LAUNCH_CONFIGS         | "launchConfigs"
    Keys.Namespace.SERVER_GROUP_INSTANCES | "serverGroupInstances"
  }

  def 'key parsing'() {
    expect:
    Keys.parse(Keys.getApplicationKey('theApp')) == [type: Keys.Namespace.APPLICATIONS.ns, application: 'theApp']
    Keys.parse(Keys.getServerGroupKey('theAsg-v001', 'account', 'region')) == [type: Keys.Namespace.SERVER_GROUPS.ns, application: 'theAsg', cluster: 'theAsg', serverGroup: 'theAsg-v001', account: 'account', region: 'region']
    Keys.parse(Keys.getClusterKey('cluster', 'application', 'account')) == [type: Keys.Namespace.CLUSTERS.ns, cluster: 'cluster', application: 'application', account: 'account']
    Keys.parse(Keys.getImageKey('image', 'region')) == [type: Keys.Namespace.IMAGES.ns, imageId: 'image', region: 'region']
    Keys.parse(Keys.getInstanceHealthKey('instanceId', 'account', 'region', 'provider')) == [type: Keys.Namespace.HEALTH.ns, instanceId: 'instanceId', account: 'account', region: 'region', provider: 'provider']
    Keys.parse(Keys.getLaunchConfigKey('launchConfig', 'region')) == [type: Keys.Namespace.LAUNCH_CONFIGS.ns, launchConfig: 'launchConfig', region: 'region']
    Keys.parse(Keys.getLoadBalancerKey('loadBalancer', 'account', 'region')) == [type: Keys.Namespace.LOAD_BALANCERS.ns, loadBalancer: 'loadBalancer', account: 'account', region: 'region']
    Keys.parse(Keys.getLoadBalancerServerGroupKey('loadBalancer', 'account', 'app-v001', 'region')) == [type: Keys.Namespace.LOAD_BALANCER_SERVER_GROUPS.ns, application: 'app', loadBalancer: 'loadBalancer', account: 'account', serverGroup: 'app-v001', region: 'region']
    Keys.parse(Keys.getServerGroupInstanceKey('asg-v001', 'instanceId', 'account', 'region')) == [type: Keys.Namespace.SERVER_GROUP_INSTANCES.ns, application: 'asg', cluster: 'asg', serverGroup: 'asg-v001', instanceId: 'instanceId', account: 'account', region: 'region']
  }

}

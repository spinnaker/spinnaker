/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.cache;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeysTest {

  static final String ACCOUNT = "test-account";
  static final String REGION = "cn-test";

  @Test
  public void testGetLoadBalancerKey() {
    String key = "alicloud:loadBalancers:test-account:cn-test:test-loadBalancer";
    String loadBalancerKey = Keys.getLoadBalancerKey("test-loadBalancer", ACCOUNT, REGION, null);
    assertTrue(key.equals(loadBalancerKey));
  }

  @Test
  public void testGetSubnetKey() {
    String key = "alicloud:subnets:test-account:cn-test:test-vswitchId";
    String subnetKey = Keys.getSubnetKey("test-vswitchId", REGION, ACCOUNT);
    assertTrue(key.equals(subnetKey));
  }

  @Test
  public void testGetImageKey() {
    String key = "alicloud:images:test-account:cn-test:test-imageId";
    String imageKey = Keys.getImageKey("test-imageId", ACCOUNT, REGION);
    assertTrue(key.equals(imageKey));
  }

  @Test
  public void testGetNamedImageKey() {
    String key = "alicloud:namedImages:test-account:test-imageName";
    String namedImageKey = Keys.getNamedImageKey(ACCOUNT, "test-imageName");
    assertTrue(key.equals(namedImageKey));
  }

  @Test
  public void testGetInstanceTypeKey() {
    String key = "alicloud:instanceTypes:test-account:cn-test:test-zoneId";
    String instanceTypeKey = Keys.getInstanceTypeKey(ACCOUNT, REGION, "test-zoneId");
    assertTrue(key.equals(instanceTypeKey));
  }

  @Test
  public void testGetSecurityGroupKey() {
    String key =
        "alicloud:securityGroups:test-account:cn-test:test-SecurityGroupName:test-SecurityGroupId";
    String securityGroupKey =
        Keys.getSecurityGroupKey(
            "test-SecurityGroupName", "test-SecurityGroupId", REGION, ACCOUNT, null);
    assertTrue(key.equals(securityGroupKey));
  }

  @Test
  public void testGetKeyPairKey() {
    String key = "alicloud:aliCloudKeyPairs:test-KeyPair:test-account:cn-test";
    String keyPairKey = Keys.getKeyPairKey("test-KeyPair", REGION, ACCOUNT);
    assertTrue(key.equals(keyPairKey));
  }

  @Test
  public void testGetServerGroupKey() {
    String key = "alicloud:serverGroups:Spin63-test-ali:test-account:cn-test:Spin63-test-ali";
    String serverGroupKey = Keys.getServerGroupKey("Spin63-test-ali", ACCOUNT, REGION);
    assertTrue(key.equals(serverGroupKey));
  }

  @Test
  public void testGetApplicationKey() {
    String key = "alicloud:applications:test-application";
    String applicationKey = Keys.getApplicationKey("test-Application");
    assertTrue(key.equals(applicationKey));
  }

  @Test
  public void testGetClusterKey() {
    String key = "alicloud:clusters:test-application:test-account:test-Cluster";
    String clusterKey = Keys.getClusterKey("test-Cluster", "test-Application", ACCOUNT);
    assertTrue(key.equals(clusterKey));
  }

  @Test
  public void testGetLaunchConfigKey() {
    String key = "alicloud:launchConfigs:test-account:cn-test:test-LaunchConfigName";
    String launchConfigKey = Keys.getLaunchConfigKey("test-LaunchConfigName", ACCOUNT, REGION);
    assertTrue(key.equals(launchConfigKey));
  }

  @Test
  public void testGetInstanceKey() {
    String key = "alicloud:instances:test-account:cn-test:test-instanceId";
    String instanceKey = Keys.getInstanceKey("test-instanceId", ACCOUNT, REGION);
    assertTrue(key.equals(instanceKey));
  }
}

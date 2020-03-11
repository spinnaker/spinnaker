/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.deploy.converters;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.ops.UpsertAliCloudLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.alicloud.model.alienum.ListenerType;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class UpsertAliCloudLoadBalancerAtomicOperationConverterTest extends CommonConverter {

  UpsertAliCloudLoadBalancerAtomicOperationConverter converter =
      new UpsertAliCloudLoadBalancerAtomicOperationConverter(clientFactory);

  @Before
  public void testBefore() {
    converter.setObjectMapper(new ObjectMapper());
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
  }

  @Test
  public void testConvertDescription() {
    AtomicOperation atomicOperation = converter.convertOperation(buildDescription());
    assertTrue(atomicOperation instanceof UpsertAliCloudLoadBalancerAtomicOperation);
  }

  @Test
  public void testConvertOperation() {
    UpsertAliCloudLoadBalancerDescription upsertAliCloudLoadBalancerDescription =
        converter.convertDescription(buildDescription());
    assertTrue(
        upsertAliCloudLoadBalancerDescription instanceof UpsertAliCloudLoadBalancerDescription);
  }

  private Map buildDescription() {
    Map<String, Object> description = new HashMap<>();
    description.put("region", REGION);
    description.put("credentials", ACCOUNT);
    description.put("loadBalancerName", "test-loadBalancerName");
    List<Map> listeners = new ArrayList<>();
    Map<String, Object> listener = new HashMap<>();
    listener.put("listenerProtocal", ListenerType.HTTP);
    listener.put("healthCheckURI", "/test/index.html");
    listener.put("healthCheck", "on");
    listener.put("healthCheckTimeout", 5);
    listener.put("unhealthyThreshold", 3);
    listener.put("healthyThreshold", 3);
    listener.put("healthCheckInterval", 2);
    listener.put("listenerPort", 80);
    listener.put("bandwidth", 112);
    listener.put("stickySession", "off");
    listener.put("backendServerPort", 90);
    listeners.add(listener);
    description.put("listeners", listeners);
    description.put("vpcId", "vpc-test");
    description.put("vSwitchId", "111111");
    return description;
  }
}

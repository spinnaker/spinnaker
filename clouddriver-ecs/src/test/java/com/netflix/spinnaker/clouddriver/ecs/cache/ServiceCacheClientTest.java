/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent;
import org.junit.Test;
import spock.lang.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class ServiceCacheClientTest extends CommonCacheClient {
  private final ObjectMapper mapper = new ObjectMapper();
  @Subject
  private final ServiceCacheClient client = new ServiceCacheClient(cacheView, mapper);

  @Test
  public void shouldConvert() {
    //Given
    ObjectMapper mapper = new ObjectMapper();
    String applicationName = "test";
    String serviceName = applicationName + "-stack-detail-v1";
    String key = Keys.getServiceKey(ACCOUNT, REGION, serviceName);
    String clusterName = "test-cluster";

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName("container-name");
    loadBalancer.setContainerPort(8080);
    loadBalancer.setLoadBalancerName("balancer-of-load");
    loadBalancer.setTargetGroupArn("target-group-arn");

    com.amazonaws.services.ecs.model.Service service = new com.amazonaws.services.ecs.model.Service();
    service.setServiceName(serviceName);
    service.setServiceArn("arn:aws:ecs:" + REGION + ":012345678910:service/" + serviceName);
    service.setClusterArn("arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName);
    service.setTaskDefinition("arn:aws:ecs:" + REGION + ":012345678910:task-definition/test-task-def:1");
    service.setRoleArn("arn:aws:ecs:" + REGION + ":012345678910:service/test-role");
    service.setDeploymentConfiguration(new DeploymentConfiguration().withMinimumHealthyPercent(50).withMaximumPercent(100));
    service.setNetworkConfiguration(new NetworkConfiguration()
      .withAwsvpcConfiguration(new AwsVpcConfiguration()
        .withSecurityGroups(Collections.singletonList("security-group-id"))
        .withSubnets(Collections.singletonList("subnet-id"))
      ));
    service.setLoadBalancers(Collections.singleton(loadBalancer));
    service.setDesiredCount(9001);
    service.setCreatedAt(new Date());
    Map<String, Object> attributes = ServiceCachingAgent.convertServiceToAttributes(ACCOUNT, REGION, service);
    attributes.put("loadBalancers", Collections.singletonList(mapper.convertValue(loadBalancer, Map.class)));

    when(cacheView.get(SERVICES.toString(), key)).thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    //When
    Service ecsService = client.get(key);

    //Then
    assertTrue("Expected the cluster name to be " + clusterName + " but got " + ecsService.getClusterName(),
      clusterName.equals(ecsService.getClusterName()));

    assertTrue("Expected the cluster ARN to be " + service.getClusterArn() + " but got " + ecsService.getClusterArn(),
      service.getClusterArn().equals(ecsService.getClusterArn()));

    assertTrue("Expected the account of the service to be " + ACCOUNT + " but got " + ecsService.getAccount(),
      ACCOUNT.equals(ecsService.getAccount()));

    assertTrue("Expected the region of the service to be " + REGION + " but got " + ecsService.getRegion(),
      REGION.equals(ecsService.getRegion()));

    assertTrue("Expected the service application name to be " + applicationName + " but got " + ecsService.getApplicationName(),
      applicationName.equals(ecsService.getApplicationName()));

    assertTrue("Expected the service name to be " + serviceName + " but got " + ecsService.getServiceName(),
      serviceName.equals(ecsService.getServiceName()));

    assertTrue("Expected the service ARN to be " + service.getServiceArn() + " but got " + ecsService.getServiceArn(),
      service.getServiceArn().equals(ecsService.getServiceArn()));

    assertTrue("Expected the role ARN of the service to be " + service.getRoleArn() + " but got " + ecsService.getRoleArn(),
      service.getRoleArn().equals(ecsService.getRoleArn()));

    assertTrue("Expected the task definition of the service to be " + service.getTaskDefinition() + " but got " + ecsService.getTaskDefinition(),
      service.getTaskDefinition().equals(ecsService.getTaskDefinition()));

    assertTrue("Expected the desired count of the service to be " + service.getDesiredCount() + " but got " + ecsService.getDesiredCount(),
      service.getDesiredCount() == ecsService.getDesiredCount());

    assertTrue("Expected the maximum percent of the service to be " + service.getDeploymentConfiguration().getMaximumPercent() + " but got " + ecsService.getMaximumPercent(),
      service.getDeploymentConfiguration().getMaximumPercent() == ecsService.getMaximumPercent());

    assertTrue("Expected the minimum healthy percent of the service to be " + service.getDeploymentConfiguration().getMinimumHealthyPercent() + " but got " + ecsService.getMinimumHealthyPercent(),
      service.getDeploymentConfiguration().getMinimumHealthyPercent() == ecsService.getMinimumHealthyPercent());

    assertTrue("Expected the created at of the service to be " + service.getCreatedAt().getTime() + " but got " + ecsService.getCreatedAt(),
      service.getCreatedAt().getTime() == ecsService.getCreatedAt());

    assertTrue("Expected the service to have 1 subnet but got " + ecsService.getSubnets().size(),
      ecsService.getSubnets().size() == 1);

    assertTrue("Expected the service to have subnet subnet-id but got " + ecsService.getSubnets().get(0),
      ecsService.getSubnets().get(0).equals("subnet-id"));

    assertTrue("Expected the service to have 1 security group but got " + ecsService.getSecurityGroups().size(),
      ecsService.getSecurityGroups().size() == 1);

    assertTrue("Expected the service to have security group security-group-id but got " + ecsService.getSecurityGroups().get(0),
      ecsService.getSecurityGroups().get(0).equals("security-group-id"));

    assertTrue("Expected the service to have 1 load balancer but got " + ecsService.getLoadBalancers().size(),
      ecsService.getLoadBalancers().size() == 1);

    assertTrue("Expected the service to have load balancer " + loadBalancer + " but got " + ecsService.getLoadBalancers().get(0),
      ecsService.getLoadBalancers().get(0).equals(loadBalancer));
  }
}

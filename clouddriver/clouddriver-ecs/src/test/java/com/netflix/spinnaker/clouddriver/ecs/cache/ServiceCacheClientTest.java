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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.TestCredential;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TestServiceCachingAgentFactory;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import spock.lang.Subject;

public class ServiceCacheClientTest extends CommonCacheClient {
  private final ObjectMapper mapper = new ObjectMapper();

  @Subject private final ServiceCacheClient client = new ServiceCacheClient(cacheView, mapper);

  @Test
  public void shouldConvert() {
    // Given
    ServiceCachingAgent agent =
        TestServiceCachingAgentFactory.create(TestCredential.named(ACCOUNT), REGION);

    String applicationName = "test";
    String serviceName = applicationName + "-stack-detail-v1";
    String key = Keys.getServiceKey(ACCOUNT, REGION, serviceName);
    String clusterName = "test-cluster";

    LoadBalancer loadBalancer =
        LoadBalancer.builder()
            .containerName("container-name")
            .containerPort(8080)
            .loadBalancerName("balancer-of-load")
            .targetGroupArn("target-group-arn")
            .build();

    Instant createdAt = Instant.now();
    software.amazon.awssdk.services.ecs.model.Service service =
        software.amazon.awssdk.services.ecs.model.Service.builder()
            .serviceName(serviceName)
            .serviceArn("arn:aws:ecs:" + REGION + ":012345678910:service/" + serviceName)
            .clusterArn("arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName)
            .taskDefinition(
                "arn:aws:ecs:" + REGION + ":012345678910:task-definition/test-task-def:1")
            .roleArn("arn:aws:ecs:" + REGION + ":012345678910:service/test-role")
            .deploymentConfiguration(
                DeploymentConfiguration.builder()
                    .minimumHealthyPercent(50)
                    .maximumPercent(100)
                    .build())
            .networkConfiguration(
                NetworkConfiguration.builder()
                    .awsvpcConfiguration(
                        AwsVpcConfiguration.builder()
                            .securityGroups("security-group-id")
                            .subnets("subnet-id")
                            .build())
                    .build())
            .loadBalancers(loadBalancer)
            .desiredCount(9001)
            .createdAt(createdAt)
            .build();
    Map<String, Object> attributes = agent.convertServiceToAttributes(service);
    // Manually build the loadBalancer map since v2 SDK objects aren't JavaBean-serializable
    Map<String, Object> loadBalancerMap = new java.util.HashMap<>();
    loadBalancerMap.put("containerName", "container-name");
    loadBalancerMap.put("containerPort", 8080);
    loadBalancerMap.put("loadBalancerName", "balancer-of-load");
    loadBalancerMap.put("targetGroupArn", "target-group-arn");
    attributes.put("loadBalancers", Collections.singletonList(loadBalancerMap));

    when(cacheView.get(SERVICES.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    Service ecsService = client.get(key);

    // Then
    assertTrue(
        clusterName.equals(ecsService.getClusterName()),
        "Expected the cluster name to be "
            + clusterName
            + " but got "
            + ecsService.getClusterName());

    assertTrue(
        service.clusterArn().equals(ecsService.getClusterArn()),
        "Expected the cluster ARN to be "
            + service.clusterArn()
            + " but got "
            + ecsService.getClusterArn());

    assertTrue(
        ACCOUNT.equals(ecsService.getAccount()),
        "Expected the account of the service to be "
            + ACCOUNT
            + " but got "
            + ecsService.getAccount());

    assertTrue(
        REGION.equals(ecsService.getRegion()),
        "Expected the region of the service to be "
            + REGION
            + " but got "
            + ecsService.getRegion());

    assertTrue(
        applicationName.equals(ecsService.getApplicationName()),
        "Expected the service application name to be "
            + applicationName
            + " but got "
            + ecsService.getApplicationName());

    assertTrue(
        serviceName.equals(ecsService.getServiceName()),
        "Expected the service name to be "
            + serviceName
            + " but got "
            + ecsService.getServiceName());

    assertTrue(
        service.serviceArn().equals(ecsService.getServiceArn()),
        "Expected the service ARN to be "
            + service.serviceArn()
            + " but got "
            + ecsService.getServiceArn());

    assertTrue(
        service.roleArn().equals(ecsService.getRoleArn()),
        "Expected the role ARN of the service to be "
            + service.roleArn()
            + " but got "
            + ecsService.getRoleArn());

    assertTrue(
        service.taskDefinition().equals(ecsService.getTaskDefinition()),
        "Expected the task definition of the service to be "
            + service.taskDefinition()
            + " but got "
            + ecsService.getTaskDefinition());

    assertTrue(
        service.desiredCount() == ecsService.getDesiredCount(),
        "Expected the desired count of the service to be "
            + service.desiredCount()
            + " but got "
            + ecsService.getDesiredCount());

    assertTrue(
        service.deploymentConfiguration().maximumPercent() == ecsService.getMaximumPercent(),
        "Expected the maximum percent of the service to be "
            + service.deploymentConfiguration().maximumPercent()
            + " but got "
            + ecsService.getMaximumPercent());

    assertTrue(
        service.deploymentConfiguration().minimumHealthyPercent()
            == ecsService.getMinimumHealthyPercent(),
        "Expected the minimum healthy percent of the service to be "
            + service.deploymentConfiguration().minimumHealthyPercent()
            + " but got "
            + ecsService.getMinimumHealthyPercent());

    assertTrue(
        service.createdAt().toEpochMilli() == ecsService.getCreatedAt(),
        "Expected the created at of the service to be "
            + service.createdAt().toEpochMilli()
            + " but got "
            + ecsService.getCreatedAt());

    assertTrue(
        ecsService.getSubnets().size() == 1,
        "Expected the service to have 1 subnet but got " + ecsService.getSubnets().size());

    assertTrue(
        ecsService.getSubnets().get(0).equals("subnet-id"),
        "Expected the service to have subnet subnet-id but got " + ecsService.getSubnets().get(0));

    assertTrue(
        ecsService.getSecurityGroups().size() == 1,
        "Expected the service to have 1 security group but got "
            + ecsService.getSecurityGroups().size());

    assertTrue(
        ecsService.getSecurityGroups().get(0).equals("security-group-id"),
        "Expected the service to have security group security-group-id but got "
            + ecsService.getSecurityGroups().get(0));

    assertTrue(
        ecsService.getLoadBalancers().size() == 1,
        "Expected the service to have 1 load balancer but got "
            + ecsService.getLoadBalancers().size());
  }
}

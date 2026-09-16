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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListClustersRequest;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.Service;
import spock.lang.Subject;

public class ServiceCacheTest extends CommonCachingAgent {
  private final ObjectMapper mapper = new ObjectMapper();

  private final ServiceCachingAgent agent =
      new ServiceCachingAgent(netflixAmazonCredentials, REGION, clientProvider, registry);
  @Subject private final ServiceCacheClient client = new ServiceCacheClient(providerCache, mapper);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getServiceKey(ACCOUNT, REGION, SERVICE_NAME_1);

    Instant createdAt = Instant.now();
    Service service =
        Service.builder()
            .serviceName(SERVICE_NAME_1)
            .serviceArn(SERVICE_ARN_1)
            .clusterArn(CLUSTER_ARN_1)
            .taskDefinition(TASK_DEFINITION_ARN_1)
            .roleArn(ROLE_ARN)
            .deploymentConfiguration(
                DeploymentConfiguration.builder()
                    .minimumHealthyPercent(50)
                    .maximumPercent(100)
                    .build())
            .loadBalancers(Collections.emptyList())
            .networkConfiguration(
                NetworkConfiguration.builder()
                    .awsvpcConfiguration(
                        AwsVpcConfiguration.builder()
                            .securityGroups(SECURITY_GROUP_1)
                            .subnets(SUBNET_ID_1)
                            .build())
                    .build())
            .desiredCount(1)
            .createdAt(createdAt)
            .build();

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(ListClustersResponse.builder().clusterArns(CLUSTER_ARN_1).build());
    when(ecs.listServices(any(ListServicesRequest.class)))
        .thenReturn(ListServicesResponse.builder().serviceArns(SERVICE_ARN_1).build());
    when(ecs.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(DescribeServicesResponse.builder().services(service).build());

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(SERVICES.toString(), key))
        .thenReturn(cacheResult.getCacheResults().get(SERVICES.toString()).iterator().next());

    // Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(SERVICES.toString());
    com.netflix.spinnaker.clouddriver.ecs.cache.model.Service ecsService = client.get(key);

    assertTrue(cacheData != null, "Expected CacheData to be returned but null is returned");
    assertTrue(cacheData.size() == 1, "Expected 1 CacheData but returned " + cacheData.size());
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        retrievedKey.equals(key),
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey);

    assertTrue(
        APP_NAME.equals(ecsService.getApplicationName()),
        "Expected the service application name to be "
            + APP_NAME
            + " but got "
            + ecsService.getApplicationName());
    assertTrue(
        SERVICE_NAME_1.equals(ecsService.getServiceName()),
        "Expected the service name to be "
            + SERVICE_NAME_1
            + " but got "
            + ecsService.getServiceName());
    assertTrue(
        SERVICE_ARN_1.equals(ecsService.getServiceArn()),
        "Expected the service ARN to be "
            + SERVICE_ARN_1
            + " but got "
            + ecsService.getServiceArn());
    assertTrue(
        CLUSTER_ARN_1.equals(ecsService.getClusterArn()),
        "Expected the service's cluster ARN to be "
            + CLUSTER_ARN_1
            + " but got "
            + ecsService.getClusterArn());
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
        ecsService.getLoadBalancers().size() == 0,
        "Expected the service to have 0 load balancer but got "
            + ecsService.getLoadBalancers().size());
    assertTrue(
        ecsService.getSubnets().size() == 1,
        "Expected the service to have 1 subnet but got " + ecsService.getSubnets().size());
    assertTrue(
        SUBNET_ID_1.equals(ecsService.getSubnets().get(0)),
        "Expected the service's subnet to be "
            + SUBNET_ID_1
            + " but got "
            + ecsService.getSubnets().get(0));
    assertTrue(
        ecsService.getSecurityGroups().size() == 1,
        "Expected the service to have 1 security group but got "
            + ecsService.getSecurityGroups().size());
    assertTrue(
        SECURITY_GROUP_1.equals(ecsService.getSecurityGroups().get(0)),
        "Expected the service's security group to be "
            + SECURITY_GROUP_1
            + " but got "
            + ecsService.getSecurityGroups().get(0));
  }
}

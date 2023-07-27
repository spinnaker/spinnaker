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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.Test;
import spock.lang.Subject;

public class ServiceCacheTest extends CommonCachingAgent {
  private final ObjectMapper mapper = new ObjectMapper();

  private final ServiceCachingAgent agent =
      new ServiceCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry);
  @Subject private final ServiceCacheClient client = new ServiceCacheClient(providerCache, mapper);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getServiceKey(ACCOUNT, REGION, SERVICE_NAME_1);

    Service service = new Service();
    service.setServiceName(SERVICE_NAME_1);
    service.setServiceArn(SERVICE_ARN_1);
    service.setClusterArn(CLUSTER_ARN_1);
    service.setTaskDefinition(TASK_DEFINITION_ARN_1);
    service.setRoleArn(ROLE_ARN);
    service.setDeploymentConfiguration(
        new DeploymentConfiguration().withMinimumHealthyPercent(50).withMaximumPercent(100));
    service.setLoadBalancers(Collections.emptyList());
    service.setNetworkConfiguration(
        new NetworkConfiguration()
            .withAwsvpcConfiguration(
                new AwsVpcConfiguration()
                    .withSecurityGroups(SECURITY_GROUP_1)
                    .withSubnets(SUBNET_ID_1)));
    service.setDesiredCount(1);
    service.setCreatedAt(new Date());

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(new ListClustersResult().withClusterArns(CLUSTER_ARN_1));
    when(ecs.listServices(any(ListServicesRequest.class)))
        .thenReturn(new ListServicesResult().withServiceArns(SERVICE_ARN_1));
    when(ecs.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(new DescribeServicesResult().withServices(service));

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
        service.getRoleArn().equals(ecsService.getRoleArn()),
        "Expected the role ARN of the service to be "
            + service.getRoleArn()
            + " but got "
            + ecsService.getRoleArn());
    assertTrue(
        service.getTaskDefinition().equals(ecsService.getTaskDefinition()),
        "Expected the task definition of the service to be "
            + service.getTaskDefinition()
            + " but got "
            + ecsService.getTaskDefinition());
    assertTrue(
        service.getDesiredCount() == ecsService.getDesiredCount(),
        "Expected the desired count of the service to be "
            + service.getDesiredCount()
            + " but got "
            + ecsService.getDesiredCount());
    assertTrue(
        service.getDeploymentConfiguration().getMaximumPercent() == ecsService.getMaximumPercent(),
        "Expected the maximum percent of the service to be "
            + service.getDeploymentConfiguration().getMaximumPercent()
            + " but got "
            + ecsService.getMaximumPercent());
    assertTrue(
        service.getDeploymentConfiguration().getMinimumHealthyPercent()
            == ecsService.getMinimumHealthyPercent(),
        "Expected the minimum healthy percent of the service to be "
            + service.getDeploymentConfiguration().getMinimumHealthyPercent()
            + " but got "
            + ecsService.getMinimumHealthyPercent());
    assertTrue(
        service.getCreatedAt().getTime() == ecsService.getCreatedAt(),
        "Expected the created at of the service to be "
            + service.getCreatedAt().getTime()
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

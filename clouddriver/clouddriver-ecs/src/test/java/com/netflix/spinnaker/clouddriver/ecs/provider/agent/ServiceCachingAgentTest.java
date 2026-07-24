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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListClustersRequest;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import spock.lang.Subject;

public class ServiceCachingAgentTest extends CommonCachingAgent {
  @Subject
  private final ServiceCachingAgent agent =
      new ServiceCachingAgent(netflixAmazonCredentials, REGION, clientProvider, registry);

  @Test
  public void shouldGetListOfServices() {
    // Given
    ListServicesResponse listServicesResult =
        ListServicesResponse.builder().serviceArns(SERVICE_ARN_1, SERVICE_ARN_2).build();
    when(ecs.listServices(any(ListServicesRequest.class))).thenReturn(listServicesResult);

    List<Service> services = new LinkedList<>();
    services.add(Service.builder().serviceArn(SERVICE_ARN_1).build());
    services.add(Service.builder().serviceArn(SERVICE_ARN_2).build());

    DescribeServicesResponse describeServicesResult =
        DescribeServicesResponse.builder().services(services).build();
    when(ecs.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(describeServicesResult);

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(ListClustersResponse.builder().clusterArns(CLUSTER_ARN_1).build());

    // When
    List<Service> returnedServices = agent.getItems(ecs, providerCache);

    // Then
    assertTrue(
        returnedServices.size() == 2,
        "Expected the list to contain 2 ECS services, but got " + returnedServices.size());
    for (Service service : returnedServices) {
      assertTrue(
          services.contains(service),
          "Expected the service to be in  "
              + services
              + " list but it was not. The service is: "
              + service);
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    // Given
    List<String> serviceNames = new LinkedList<>();
    serviceNames.add(SERVICE_NAME_1);
    serviceNames.add(SERVICE_NAME_2);

    List<String> serviceArns = new LinkedList<>();
    serviceArns.add(SERVICE_ARN_1);
    serviceArns.add(SERVICE_ARN_2);

    List<Service> services = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (int x = 0; x < serviceArns.size(); x++) {
      keys.add(Keys.getServiceKey(ACCOUNT, REGION, serviceNames.get(x)));

      services.add(
          Service.builder()
              .clusterArn(CLUSTER_ARN_1)
              .serviceArn(serviceArns.get(x))
              .serviceName(serviceNames.get(x))
              .taskDefinition(TASK_DEFINITION_ARN_1)
              .roleArn(ROLE_ARN)
              .deploymentConfiguration(
                  DeploymentConfiguration.builder()
                      .minimumHealthyPercent(50)
                      .maximumPercent(100)
                      .build())
              .loadBalancers(Collections.emptyList())
              .desiredCount(1)
              .createdAt(Instant.now())
              .build());
    }

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(services);

    // Then
    assertTrue(
        dataMap.keySet().size() == 2,
        "Expected the data map to contain 2 namespaces, but it contains "
            + dataMap.keySet().size()
            + " namespaces.");
    assertTrue(
        dataMap.containsKey(SERVICES.toString()),
        "Expected the data map to contain "
            + SERVICES.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.");
    assertTrue(
        dataMap.containsKey(ECS_CLUSTERS.toString()),
        "Expected the data map to contain "
            + ECS_CLUSTERS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.");
    assertTrue(
        dataMap.get(SERVICES.toString()).size() == 2,
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(SERVICES.toString()).size());

    for (CacheData cacheData : dataMap.get(SERVICES.toString())) {
      assertTrue(
          keys.contains(cacheData.getId()),
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".");
      assertTrue(
          serviceArns.contains(cacheData.getAttributes().get("serviceArn")),
          "Expected the service ARN to be one of the following ARNs: "
              + serviceArns.toString()
              + ". The service ARN is: "
              + cacheData.getAttributes().get("serviceArn")
              + ".");
    }
  }
}

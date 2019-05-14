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
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.Service;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import spock.lang.Subject;

public class ServiceCachingAgentTest extends CommonCachingAgent {
  @Subject
  private final ServiceCachingAgent agent =
      new ServiceCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldGetListOfServices() {
    // Given
    ListServicesResult listServicesResult =
        new ListServicesResult().withServiceArns(SERVICE_ARN_1, SERVICE_ARN_2);
    when(ecs.listServices(any(ListServicesRequest.class))).thenReturn(listServicesResult);

    List<Service> services = new LinkedList<>();
    services.add(new Service().withServiceArn(SERVICE_ARN_1));
    services.add(new Service().withServiceArn(SERVICE_ARN_2));

    DescribeServicesResult describeServicesResult =
        new DescribeServicesResult().withServices(services);
    when(ecs.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(describeServicesResult);

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(new ListClustersResult().withClusterArns(CLUSTER_ARN_1));

    // When
    List<Service> returnedServices = agent.getItems(ecs, providerCache);

    // Then
    assertTrue(
        "Expected the list to contain 2 ECS services, but got " + returnedServices.size(),
        returnedServices.size() == 2);
    for (Service service : returnedServices) {
      assertTrue(
          "Expected the service to be in  "
              + services
              + " list but it was not. The service is: "
              + service,
          services.contains(service));
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
          new Service()
              .withClusterArn(CLUSTER_ARN_1)
              .withServiceArn(serviceArns.get(x))
              .withServiceName(serviceNames.get(x))
              .withTaskDefinition(TASK_DEFINITION_ARN_1)
              .withRoleArn(ROLE_ARN)
              .withDeploymentConfiguration(
                  new DeploymentConfiguration()
                      .withMinimumHealthyPercent(50)
                      .withMaximumPercent(100))
              .withLoadBalancers(Collections.emptyList())
              .withDesiredCount(1)
              .withCreatedAt(new Date()));
    }

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(services);

    // Then
    assertTrue(
        "Expected the data map to contain 2 namespaces, but it contains "
            + dataMap.keySet().size()
            + " namespaces.",
        dataMap.keySet().size() == 2);
    assertTrue(
        "Expected the data map to contain "
            + SERVICES.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.",
        dataMap.containsKey(SERVICES.toString()));
    assertTrue(
        "Expected the data map to contain "
            + ECS_CLUSTERS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.",
        dataMap.containsKey(ECS_CLUSTERS.toString()));
    assertTrue(
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(SERVICES.toString()).size(),
        dataMap.get(SERVICES.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(SERVICES.toString())) {
      assertTrue(
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".",
          keys.contains(cacheData.getId()));
      assertTrue(
          "Expected the service ARN to be one of the following ARNs: "
              + serviceArns.toString()
              + ". The service ARN is: "
              + cacheData.getAttributes().get("serviceArn")
              + ".",
          serviceArns.contains(cacheData.getAttributes().get("serviceArn")));
    }
  }
}

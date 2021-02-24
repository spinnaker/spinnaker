/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Application;
import java.util.*;
import org.junit.Test;
import spock.lang.Subject;

public class ApplicationCachingAgentTest extends CommonCachingAgent {
  ObjectMapper mapper = new ObjectMapper();

  @Subject
  private final ApplicationCachingAgent agent =
      new ApplicationCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry, mapper);

  @Test
  public void shouldGetListOfApplications() {
    // Given
    Map<String, Object> serviceAttr = new HashMap<>();
    serviceAttr.put("account", ACCOUNT);
    serviceAttr.put("region", REGION);
    serviceAttr.put("taskDefinition", TASK_DEFINITION_ARN_1);
    serviceAttr.put("desiredCount", 1);
    serviceAttr.put("applicationName", APP_NAME);
    serviceAttr.put("serviceName", SERVICE_NAME_1);
    serviceAttr.put("maximumPercent", 200);
    serviceAttr.put("minimumHealthyPercent", 50);
    serviceAttr.put("createdAt", 8976543L);

    DefaultCacheData serviceCache =
        new DefaultCacheData("test-service", serviceAttr, Collections.emptyMap());
    when(providerCache.filterIdentifiers(
            SERVICES.toString(), "ecs;services;test-account;us-west-2;*"))
        .thenReturn(Collections.singletonList("test-service"));
    when(providerCache.getAll(anyString())).thenReturn(Collections.singletonList(serviceCache));

    // When
    List<Application> returnedApplications = agent.getItems(ecs, providerCache);

    // Then
    assertEquals(
        "Expected the list to contain 1 ECS application, but got " + returnedApplications.size(),
        1,
        returnedApplications.size());
    for (Application application : returnedApplications) {
      assertEquals(
          "Expected the application to be  " + APP_NAME + " but it was: " + application.getName(),
          application.getName(),
          APP_NAME);
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    // Given
    List<String> applicationNames = new LinkedList<>();
    applicationNames.add(APP_NAME);
    applicationNames.add(APP_NAME_2);

    List<Application> applications = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (String appName : applicationNames) {
      keys.add(Keys.getApplicationKey(appName));
      Application application = new Application();
      application.setName(appName);
      applications.add(application);
    }

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(applications);

    // Then
    assertTrue(
        "Expected the data map to contain 1 namespaces, but it contains "
            + dataMap.keySet().size()
            + " namespaces.",
        dataMap.keySet().size() == 1);
    assertTrue(
        "Expected the data map to contain "
            + APPLICATIONS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.",
        dataMap.containsKey(APPLICATIONS.toString()));
    assertTrue(
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(APPLICATIONS.toString()).size(),
        dataMap.get(APPLICATIONS.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(APPLICATIONS.toString())) {
      assertTrue(
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".",
          keys.contains(cacheData.getId()));
      assertTrue(
          "Expected the application name to be one of the following "
              + applicationNames.toString()
              + ". The application name is: "
              + cacheData.getAttributes().get("name")
              + ".",
          applicationNames.contains(cacheData.getAttributes().get("name")));
    }
  }
}

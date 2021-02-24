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
import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ApplicationCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import java.util.*;
import org.junit.Assert;
import org.junit.Test;
import spock.lang.Subject;

public class ApplicationCacheTest extends CommonCachingAgent {
  private final ObjectMapper mapper = new ObjectMapper();

  @Subject
  private final ApplicationCachingAgent agent =
      new ApplicationCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry, mapper);

  @Subject private final ApplicationCacheClient client = new ApplicationCacheClient(providerCache);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getApplicationKey(APP_NAME);

    Map<String, String> attributes = new HashMap<>();
    attributes.put("name", APP_NAME);

    Map<String, Set<String>> clusterNames = new HashMap<>();
    clusterNames.put(ACCOUNT, Sets.newHashSet(SERVICE_NAME_1));

    EcsApplication application = new EcsApplication(APP_NAME, attributes, clusterNames);

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
            APPLICATIONS.toString(), "ecs;services;test-account;us-west-2;*"))
        .thenReturn(Collections.singletonList("test-service"));
    when(providerCache.getAll(anyString())).thenReturn(Collections.singletonList(serviceCache));

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(APPLICATIONS.toString(), key))
        .thenReturn(cacheResult.getCacheResults().get(APPLICATIONS.toString()).iterator().next());
    Application retrievedApplication = client.get(key);

    // Then
    Collection<CacheData> cacheData = cacheResult.getCacheResults().get(APPLICATIONS.toString());
    assertNotNull("Expected CacheData to be returned but null is returned", cacheData);
    assertEquals("Expected 1 CacheData but returned " + cacheData.size(), 1, cacheData.size());
    String retrievedKey = cacheData.iterator().next().getId();
    assertEquals(
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey,
        retrievedKey,
        key);

    Assert.assertEquals(
        "Expected the application to be " + application + " but got " + retrievedApplication,
        application,
        retrievedApplication);
  }
}

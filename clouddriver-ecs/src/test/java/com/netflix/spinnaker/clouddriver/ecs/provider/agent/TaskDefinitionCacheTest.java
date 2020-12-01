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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;
import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import java.util.*;
import org.junit.Assert;
import org.junit.Test;
import spock.lang.Subject;

public class TaskDefinitionCacheTest extends CommonCachingAgent {
  private final ObjectMapper mapper = new ObjectMapper();

  @Subject
  private final TaskDefinitionCachingAgent agent =
      new TaskDefinitionCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry, mapper);

  @Subject
  private final TaskDefinitionCacheClient client =
      new TaskDefinitionCacheClient(providerCache, mapper);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getTaskDefinitionKey(ACCOUNT, REGION, TASK_DEFINITION_ARN_1);

    TaskDefinition taskDefinition = new TaskDefinition();
    taskDefinition.setTaskDefinitionArn(TASK_DEFINITION_ARN_1);
    taskDefinition.setContainerDefinitions(Collections.emptyList());

    Map<String, Object> serviceAttr = new HashMap<>();
    serviceAttr.put("taskDefinition", TASK_DEFINITION_ARN_1);
    serviceAttr.put("desiredCount", 1);
    serviceAttr.put("serviceName", SERVICE_NAME_1);
    serviceAttr.put("maximumPercent", 200);
    serviceAttr.put("minimumHealthyPercent", 50);
    serviceAttr.put("createdAt", 8976543L);

    DefaultCacheData serviceCache =
        new DefaultCacheData("test-service", serviceAttr, Collections.emptyMap());

    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
        .thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(taskDefinition));
    when(providerCache.filterIdentifiers(
            SERVICES.toString(), "ecs;services;test-account;us-west-2;*"))
        .thenReturn(Collections.singletonList("test-service"));
    when(providerCache.getAll(anyString(), any(Set.class)))
        .thenReturn(Collections.singletonList(serviceCache));

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(TASK_DEFINITIONS.toString(), key))
        .thenReturn(
            cacheResult.getCacheResults().get(TASK_DEFINITIONS.toString()).iterator().next());
    TaskDefinition retrievedTaskDefinition = client.get(key);

    // Then
    Collection<CacheData> cacheData =
        cacheResult.getCacheResults().get(TASK_DEFINITIONS.toString());
    assertNotNull("Expected CacheData to be returned but null is returned", cacheData);
    assertEquals("Expected 1 CacheData but returned " + cacheData.size(), 1, cacheData.size());
    String retrievedKey = cacheData.iterator().next().getId();
    assertEquals(
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey,
        retrievedKey,
        key);

    Assert.assertEquals(
        "Expected the task definition to be "
            + taskDefinition
            + " but got "
            + retrievedTaskDefinition,
        taskDefinition,
        retrievedTaskDefinition);
  }
}

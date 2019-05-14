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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsRequest;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import java.util.Collection;
import java.util.Collections;
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

    when(ecs.listTaskDefinitions(any(ListTaskDefinitionsRequest.class)))
        .thenReturn(new ListTaskDefinitionsResult().withTaskDefinitionArns(TASK_DEFINITION_ARN_1));
    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
        .thenReturn(new DescribeTaskDefinitionResult().withTaskDefinition(taskDefinition));

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(TASK_DEFINITIONS.toString(), key))
        .thenReturn(
            cacheResult.getCacheResults().get(TASK_DEFINITIONS.toString()).iterator().next());
    TaskDefinition retrievedTaskDefinition = client.get(key);

    // Then
    Collection<CacheData> cacheData =
        cacheResult.getCacheResults().get(TASK_DEFINITIONS.toString());
    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey,
        retrievedKey.equals(key));

    Assert.assertTrue(
        "Expected the task definition to be "
            + taskDefinition
            + " but got "
            + retrievedTaskDefinition,
        taskDefinition.equals(retrievedTaskDefinition));
  }
}

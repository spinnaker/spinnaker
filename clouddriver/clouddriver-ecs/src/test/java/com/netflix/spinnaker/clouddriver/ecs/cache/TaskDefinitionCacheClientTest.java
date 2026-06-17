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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskDefinitionCachingAgent;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spock.lang.Subject;

public class TaskDefinitionCacheClientTest extends CommonCacheClient {
  ObjectMapper mapper = new ObjectMapper();

  @Subject
  private final TaskDefinitionCacheClient client = new TaskDefinitionCacheClient(cacheView, mapper);

  @Test
  public void shouldConvert() {
    // Given
    ObjectMapper mapper = new ObjectMapper();
    String taskDefinitionArn =
        "arn:aws:ecs:" + REGION + ":012345678910:task-definition/hello_world:10";
    String key = Keys.getTaskDefinitionKey(ACCOUNT, REGION, taskDefinitionArn);

    software.amazon.awssdk.services.ecs.model.ContainerDefinition containerDefinition =
        software.amazon.awssdk.services.ecs.model.ContainerDefinition.builder()
            .cpu(256)
            .memory(512)
            .name("container-definition-name")
            .build();

    software.amazon.awssdk.services.ecs.model.TaskDefinition v2TaskDefinition =
        software.amazon.awssdk.services.ecs.model.TaskDefinition.builder()
            .taskDefinitionArn(taskDefinitionArn)
            .memory("1")
            .cpu("2")
            .containerDefinitions(containerDefinition)
            .build();

    Map<String, Object> attributes =
        TaskDefinitionCachingAgent.convertTaskDefinitionToAttributes(v2TaskDefinition);

    // Manually build the containerDefinitions map since v2 SDK objects aren't JavaBean-serializable
    Map<String, Object> containerDefMap = new java.util.HashMap<>();
    containerDefMap.put("cpu", 256);
    containerDefMap.put("memory", 512);
    containerDefMap.put("name", "container-definition-name");
    attributes.put("containerDefinitions", Collections.singletonList(containerDefMap));
    when(cacheView.get(TASK_DEFINITIONS.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    TaskDefinition retrievedTaskDefinition = client.get(key);

    // Then
    assertTrue(
        taskDefinitionArn.equals(retrievedTaskDefinition.getTaskDefinitionArn()),
        "Expected the task definition ARN to be "
            + taskDefinitionArn
            + " but got "
            + retrievedTaskDefinition.getTaskDefinitionArn());
    assertTrue(
        "1".equals(retrievedTaskDefinition.getMemory()),
        "Expected memory to be 1 but got " + retrievedTaskDefinition.getMemory());
    assertTrue(
        "2".equals(retrievedTaskDefinition.getCpu()),
        "Expected cpu to be 2 but got " + retrievedTaskDefinition.getCpu());
  }
}

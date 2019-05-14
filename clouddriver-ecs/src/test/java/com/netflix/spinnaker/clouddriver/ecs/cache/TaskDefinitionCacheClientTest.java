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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskDefinitionCachingAgent;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
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

    ContainerDefinition containerDefinition = new ContainerDefinition();
    containerDefinition.setCpu(256);
    containerDefinition.setMemory(512);
    containerDefinition.setName("container-definition-name");

    TaskDefinition taskDefinition = new TaskDefinition();
    taskDefinition.setTaskDefinitionArn(taskDefinitionArn);
    taskDefinition.setMemory("1");
    taskDefinition.setCpu("2");
    taskDefinition.setContainerDefinitions(Collections.singleton(containerDefinition));

    Map<String, Object> attributes =
        TaskDefinitionCachingAgent.convertTaskDefinitionToAttributes(taskDefinition);
    attributes.put(
        "containerDefinitions",
        Collections.singletonList(mapper.convertValue(containerDefinition, Map.class)));
    when(cacheView.get(TASK_DEFINITIONS.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    TaskDefinition retrievedTaskDefinition = client.get(key);

    // Then
    assertTrue(
        "Expected the task definition to be "
            + taskDefinition
            + " but got "
            + retrievedTaskDefinition,
        taskDefinition.equals(retrievedTaskDefinition));
  }
}

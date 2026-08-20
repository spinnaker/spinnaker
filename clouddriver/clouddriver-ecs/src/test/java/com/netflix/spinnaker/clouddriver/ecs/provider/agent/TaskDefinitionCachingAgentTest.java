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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.ContainerDefinition;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.HealthCheck;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.PortMapping;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import spock.lang.Subject;

public class TaskDefinitionCachingAgentTest extends CommonCachingAgent {
  // a described task definition is distinguishable from a cached one, so that reusing the cache can
  // be told apart from making a describe call
  private static final String CACHED_IMAGE = "cached-image";
  private static final String DESCRIBED_IMAGE = "described-image";
  private static final int LOAD_BALANCED_CONTAINER_PORT = 7007;

  ObjectMapper mapper = new ObjectMapper().registerModule(new AwsSdkV2Module());

  @Subject
  private final TaskDefinitionCachingAgent agent =
      new TaskDefinitionCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, registry, mapper);

  @Test
  public void shouldGetListOfTaskDefinitions() {
    // Given
    Map<String, Object> serviceAttr = new HashMap<>();
    serviceAttr.put("taskDefinition", TASK_DEFINITION_ARN_1);
    serviceAttr.put("desiredCount", 1);
    serviceAttr.put("serviceName", SERVICE_NAME_1);
    serviceAttr.put("maximumPercent", 200);
    serviceAttr.put("minimumHealthyPercent", 50);
    serviceAttr.put("createdAt", 8976543L);

    DefaultCacheData serviceCache =
        new DefaultCacheData("test-service", serviceAttr, Collections.emptyMap());
    when(providerCache.filterIdentifiers(
            SERVICES.toString(), "ecs;services;test-account;us-west-2;*"))
        .thenReturn(Set.of("test-service"));
    when(providerCache.getAll(anyString(), any(Set.class)))
        .thenReturn(Collections.singletonList(serviceCache));

    DescribeTaskDefinitionResponse describeTaskDefinitionResult =
        DescribeTaskDefinitionResponse.builder()
            .taskDefinition(
                TaskDefinition.builder().taskDefinitionArn(TASK_DEFINITION_ARN_1).build())
            .build();
    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
        .thenReturn(describeTaskDefinitionResult);

    // When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    // Then
    assertEquals(
        1,
        returnedTaskDefs.size(),
        "Expected the list to contain 1 ECS task definition, but got " + returnedTaskDefs.size());
    for (TaskDefinition taskDef : returnedTaskDefs) {
      assertEquals(
          taskDef.taskDefinitionArn(),
          TASK_DEFINITION_ARN_1,
          "Expected the task definition ARN to be  "
              + TASK_DEFINITION_ARN_1
              + " but it was: "
              + taskDef.taskDefinitionArn());
    }
  }

  @Test
  public void shouldRetainCachedTaskDefinitions() {
    // Given
    givenServiceCache(null);
    givenCachedTaskDefinition(taskDefinition(CACHED_IMAGE));
    givenDescribedTaskDefinition(taskDefinition(DESCRIBED_IMAGE));

    // When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    // Then
    assertEquals(
        1,
        returnedTaskDefs.size(),
        "Expected the list to contain 1 ECS task definition, but got " + returnedTaskDefs.size());
    for (TaskDefinition taskDef : returnedTaskDefs) {
      assertEquals(
          taskDef.taskDefinitionArn(),
          TASK_DEFINITION_ARN_1,
          "Expected the task definition ARN to be  "
              + TASK_DEFINITION_ARN_1
              + " but it was: "
              + taskDef.taskDefinitionArn());
      assertEquals(
          CACHED_IMAGE,
          taskDef.containerDefinitions().get(0).image(),
          "Expected the cached task definition to be reused rather than described again");
    }
  }

  @Test
  public void shouldDescribeCachedTaskDefinitionWithoutContainerDefinitions() {
    // Given
    givenServiceCache(null);

    // an entry cached without container definitions carries nothing the ECS provider can read
    Map<String, Object> taskDefAttr = new HashMap<>();
    taskDefAttr.put("taskDefinitionArn", TASK_DEFINITION_ARN_1);
    when(providerCache.get(
            TASK_DEFINITIONS.toString(),
            "ecs;taskDefinitions;test-account;us-west-2;" + TASK_DEFINITION_ARN_1))
        .thenReturn(
            new DefaultCacheData(TASK_DEFINITION_ARN_1, taskDefAttr, Collections.emptyMap()));
    givenDescribedTaskDefinition(taskDefinition(DESCRIBED_IMAGE));

    // When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    // Then
    assertEquals(1, returnedTaskDefs.size());
    assertEquals(
        DESCRIBED_IMAGE,
        returnedTaskDefs.get(0).containerDefinitions().get(0).image(),
        "Expected an incomplete cache entry to be described again");
  }

  @Test
  public void shouldDescribeCachedTaskDefinitionMissingLoadBalancedPortMapping() {
    // Given
    givenServiceCache(LOAD_BALANCED_CONTAINER_PORT);

    // without the load balanced port mapping, TaskHealthCachingAgent cannot resolve task health, so
    // reusing this entry would keep load balancer health unknown for as long as it stays cached
    givenCachedTaskDefinition(taskDefinition(CACHED_IMAGE));
    givenDescribedTaskDefinition(taskDefinition(DESCRIBED_IMAGE, LOAD_BALANCED_CONTAINER_PORT));

    // When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    // Then
    assertEquals(1, returnedTaskDefs.size());
    assertEquals(
        DESCRIBED_IMAGE,
        returnedTaskDefs.get(0).containerDefinitions().get(0).image(),
        "Expected a cache entry missing the load balanced port mapping to be described again");
    assertEquals(
        LOAD_BALANCED_CONTAINER_PORT,
        returnedTaskDefs
            .get(0)
            .containerDefinitions()
            .get(0)
            .portMappings()
            .get(0)
            .containerPort());
  }

  @Test
  public void shouldRetainCachedTaskDefinitionWithLoadBalancedPortMapping() {
    // Given
    givenServiceCache(LOAD_BALANCED_CONTAINER_PORT);
    givenCachedTaskDefinition(taskDefinition(CACHED_IMAGE, LOAD_BALANCED_CONTAINER_PORT));
    givenDescribedTaskDefinition(taskDefinition(DESCRIBED_IMAGE, LOAD_BALANCED_CONTAINER_PORT));

    // When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    // Then
    assertEquals(1, returnedTaskDefs.size());
    assertEquals(
        CACHED_IMAGE,
        returnedTaskDefs.get(0).containerDefinitions().get(0).image(),
        "Expected a complete cache entry to be reused rather than described again");
  }

  private static TaskDefinition taskDefinition(String image, int... containerPorts) {
    List<PortMapping> portMappings = new ArrayList<>();
    for (int containerPort : containerPorts) {
      portMappings.add(PortMapping.builder().containerPort(containerPort).build());
    }

    return TaskDefinition.builder()
        .taskDefinitionArn(TASK_DEFINITION_ARN_1)
        .containerDefinitions(
            ContainerDefinition.builder()
                .name("test-container")
                .image(image)
                .portMappings(portMappings)
                .build())
        .build();
  }

  private void givenServiceCache(Integer loadBalancedContainerPort) {
    Map<String, Object> serviceAttr = new HashMap<>();
    serviceAttr.put("taskDefinition", TASK_DEFINITION_ARN_1);
    serviceAttr.put("desiredCount", 1);
    serviceAttr.put("serviceName", SERVICE_NAME_1);
    serviceAttr.put("maximumPercent", 200);
    serviceAttr.put("minimumHealthyPercent", 50);
    serviceAttr.put("createdAt", 8976543L);

    if (loadBalancedContainerPort != null) {
      Map<String, Object> loadBalancer = new HashMap<>();
      loadBalancer.put("containerPort", loadBalancedContainerPort);
      loadBalancer.put("containerName", "test-container");
      loadBalancer.put("targetGroupArn", "arn:aws:elasticloadbalancing:targetgroup/test");
      serviceAttr.put("loadBalancers", Collections.singletonList(loadBalancer));
    }

    DefaultCacheData serviceCache =
        new DefaultCacheData("test-service", serviceAttr, Collections.emptyMap());
    when(providerCache.filterIdentifiers(
            SERVICES.toString(), "ecs;services;test-account;us-west-2;*"))
        .thenReturn(Collections.singletonList("test-service"));
    when(providerCache.getAll(anyString(), any(Set.class)))
        .thenReturn(Collections.singletonList(serviceCache));
  }

  private void givenCachedTaskDefinition(TaskDefinition taskDefinition) {
    // the agent caches through convertTaskDefinitionToAttributes, so the entry a later run reads
    // back is exactly what an earlier run wrote
    when(providerCache.get(
            TASK_DEFINITIONS.toString(),
            "ecs;taskDefinitions;test-account;us-west-2;" + TASK_DEFINITION_ARN_1))
        .thenReturn(
            new DefaultCacheData(
                TASK_DEFINITION_ARN_1,
                TaskDefinitionCachingAgent.convertTaskDefinitionToAttributes(taskDefinition),
                Collections.emptyMap()));
  }

  private void givenDescribedTaskDefinition(TaskDefinition taskDefinition) {
    when(ecs.describeTaskDefinition(any(DescribeTaskDefinitionRequest.class)))
        .thenReturn(
            DescribeTaskDefinitionResponse.builder().taskDefinition(taskDefinition).build());
  }

  @Test
  public void shouldRetainAllFieldsOfCachedTaskDefinitions() {
    // Given
    Map<String, Object> serviceAttr = new HashMap<>();
    serviceAttr.put("taskDefinition", TASK_DEFINITION_ARN_1);
    serviceAttr.put("desiredCount", 1);
    serviceAttr.put("serviceName", SERVICE_NAME_1);
    serviceAttr.put("maximumPercent", 200);
    serviceAttr.put("minimumHealthyPercent", 50);
    serviceAttr.put("createdAt", 8976543L);

    DefaultCacheData serviceCache =
        new DefaultCacheData("test-service", serviceAttr, Collections.emptyMap());
    when(providerCache.filterIdentifiers(
            SERVICES.toString(), "ecs;services;test-account;us-west-2;*"))
        .thenReturn(Collections.singleton("test-service"));
    when(providerCache.getAll(anyString(), any(Set.class)))
        .thenReturn(Collections.singletonList(serviceCache));

    TaskDefinition cachedTaskDef =
        TaskDefinition.builder()
            .taskDefinitionArn(TASK_DEFINITION_ARN_1)
            .taskRoleArn("task-role-arn")
            .cpu("256")
            .memory("512")
            .containerDefinitions(
                ContainerDefinition.builder()
                    .name("test-container")
                    .image("test-image")
                    .memoryReservation(256)
                    .portMappings(PortMapping.builder().containerPort(7007).hostPort(7007).build())
                    .environment(KeyValuePair.builder().name("ENV_VAR").value("value").build())
                    .healthCheck(HealthCheck.builder().command("CMD-SHELL", "exit 0").build())
                    .build())
            .build();

    // the agent caches attributes through convertTaskDefinitionToAttributes, so the cache entry a
    // later run reads back is exactly what a previous run wrote
    Map<String, Object> taskDefAttr =
        TaskDefinitionCachingAgent.convertTaskDefinitionToAttributes(cachedTaskDef);
    DefaultCacheData taskDefCache =
        new DefaultCacheData(TASK_DEFINITION_ARN_1, taskDefAttr, Collections.emptyMap());
    when(providerCache.get(
            TASK_DEFINITIONS.toString(),
            "ecs;taskDefinitions;test-account;us-west-2;" + TASK_DEFINITION_ARN_1))
        .thenReturn(taskDefCache);

    // When
    List<TaskDefinition> returnedTaskDefs = agent.getItems(ecs, providerCache);

    // Then no field may be dropped: the loss would be permanent, since a cached task definition is
    // never described again. (The models are not compared with equals() because the round trip
    // materializes the SDK's auto-construct collections, e.g. Links=[], as explicitly set empties.)
    assertEquals(1, returnedTaskDefs.size());
    TaskDefinition returned = returnedTaskDefs.get(0);
    assertEquals(TASK_DEFINITION_ARN_1, returned.taskDefinitionArn());
    assertEquals("task-role-arn", returned.taskRoleArn());
    assertEquals("256", returned.cpu());
    assertEquals("512", returned.memory());
    assertEquals(1, returned.containerDefinitions().size());

    ContainerDefinition returnedContainer = returned.containerDefinitions().get(0);
    ContainerDefinition cachedContainer = cachedTaskDef.containerDefinitions().get(0);
    assertEquals(cachedContainer.name(), returnedContainer.name());
    assertEquals(cachedContainer.image(), returnedContainer.image());
    assertEquals(cachedContainer.memoryReservation(), returnedContainer.memoryReservation());
    // port mappings drive load balancer health: without them TaskHealthCachingAgent skips the task
    assertEquals(cachedContainer.portMappings(), returnedContainer.portMappings());
    assertEquals(cachedContainer.environment(), returnedContainer.environment());
    assertEquals(cachedContainer.healthCheck(), returnedContainer.healthCheck());
  }

  @Test
  public void shouldGenerateFreshData() {
    // Given
    List<String> taskDefinitionArns = new LinkedList<>();
    taskDefinitionArns.add(TASK_DEFINITION_ARN_1);
    taskDefinitionArns.add(TASK_DEFINITION_ARN_2);

    List<TaskDefinition> tasks = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (String taskDefArn : taskDefinitionArns) {
      keys.add(Keys.getTaskDefinitionKey(ACCOUNT, REGION, taskDefArn));

      tasks.add(
          TaskDefinition.builder()
              .taskDefinitionArn(taskDefArn)
              .containerDefinitions(Collections.emptyList())
              .build());
    }

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(tasks);

    // Then
    assertTrue(
        dataMap.keySet().size() == 1,
        "Expected the data map to contain 1 namespaces, but it contains "
            + dataMap.keySet().size()
            + " namespaces.");
    assertTrue(
        dataMap.containsKey(TASK_DEFINITIONS.toString()),
        "Expected the data map to contain "
            + TASK_DEFINITIONS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.");
    assertTrue(
        dataMap.get(TASK_DEFINITIONS.toString()).size() == 2,
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(TASK_DEFINITIONS.toString()).size());

    for (CacheData cacheData : dataMap.get(TASK_DEFINITIONS.toString())) {
      assertTrue(
          keys.contains(cacheData.getId()),
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".");
      assertTrue(
          taskDefinitionArns.contains(cacheData.getAttributes().get("taskDefinitionArn")),
          "Expected the task definition ARN to be one of the following ARNs: "
              + taskDefinitionArns.toString()
              + ". The task definition  ARN is: "
              + cacheData.getAttributes().get("taskDefinitionArn")
              + ".");
    }
  }
}

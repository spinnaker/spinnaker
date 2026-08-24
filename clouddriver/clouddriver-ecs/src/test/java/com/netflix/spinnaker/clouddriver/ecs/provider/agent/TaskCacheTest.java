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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListClustersRequest;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;
import spock.lang.Subject;

public class TaskCacheTest extends CommonCachingAgent {
  private final ObjectMapper mapper = new ObjectMapper();

  @Subject
  private final TaskCachingAgent agent =
      new TaskCachingAgent(netflixAmazonCredentials, REGION, clientProvider, registry);

  @Subject private final TaskCacheClient client = new TaskCacheClient(providerCache, mapper);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    String key = Keys.getTaskKey(ACCOUNT, REGION, TASK_ID_1);

    Instant startedAt = Instant.now();
    Task task =
        Task.builder()
            .taskArn(TASK_ARN_1)
            .clusterArn(CLUSTER_ARN_1)
            .containerInstanceArn(CONTAINER_INSTANCE_ARN_1)
            .group("group" + SERVICE_NAME_1)
            .containers(Collections.emptyList())
            .lastStatus(STATUS)
            .desiredStatus(STATUS)
            .startedAt(startedAt)
            .availabilityZone(REGION + "a")
            .build();

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(ListClustersResponse.builder().clusterArns(CLUSTER_ARN_1).build());
    when(ecs.listTasks(any(ListTasksRequest.class)))
        .thenReturn(ListTasksResponse.builder().taskArns(TASK_ARN_1).build());
    when(ecs.describeTasks(any(DescribeTasksRequest.class)))
        .thenReturn(DescribeTasksResponse.builder().tasks(task).build());

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    when(providerCache.get(TASKS.toString(), key))
        .thenReturn(cacheResult.getCacheResults().get(TASKS.toString()).iterator().next());

    // Then
    Collection<CacheData> cacheData =
        cacheResult.getCacheResults().get(Keys.Namespace.TASKS.toString());
    com.netflix.spinnaker.clouddriver.ecs.cache.model.Task ecsTask = client.get(key);

    assertTrue(cacheData != null, "Expected CacheData to be returned but null is returned");
    assertTrue(cacheData.size() == 1, "Expected 1 CacheData but returned " + cacheData.size());
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        cacheData.size() == 1,
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey);

    assertTrue(
        CLUSTER_ARN_1.equals(ecsTask.getClusterArn()),
        "Expected the cluster ARN to be " + CLUSTER_ARN_1 + " but got " + ecsTask.getClusterArn());

    assertTrue(
        TASK_ARN_1.equals(ecsTask.getTaskArn()),
        "Expected the task ARN to be " + TASK_ARN_1 + " but got " + ecsTask.getTaskArn());

    assertTrue(
        task.containerInstanceArn().equals(ecsTask.getContainerInstanceArn()),
        "Expected the container instance ARN name to be "
            + task.containerInstanceArn()
            + " but got "
            + ecsTask.getContainerInstanceArn());

    assertTrue(
        task.group().equals(ecsTask.getGroup()),
        "Expected the group to be " + task.group() + " but got " + ecsTask.getGroup());

    assertTrue(
        task.lastStatus().equals(ecsTask.getLastStatus()),
        "Expected the last status to be "
            + task.lastStatus()
            + " but got "
            + ecsTask.getLastStatus());

    assertTrue(
        task.desiredStatus().equals(ecsTask.getDesiredStatus()),
        "Expected the desired status to be "
            + task.desiredStatus()
            + " but got "
            + ecsTask.getDesiredStatus());

    assertTrue(
        task.startedAt().toEpochMilli() == ecsTask.getStartedAt(),
        "Expected the started at to be "
            + task.startedAt().toEpochMilli()
            + " but got "
            + ecsTask.getStartedAt());

    assertTrue(
        task.availabilityZone().equals(ecsTask.getAvailabilityZone()),
        "Expected the availability zone to be "
            + task.availabilityZone()
            + " but got "
            + ecsTask.getAvailabilityZone());

    assertTrue(
        task.containers().size() == 0,
        "Expected the task to have 0 containers but got " + task.containers().size());
  }
}

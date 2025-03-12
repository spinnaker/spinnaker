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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskCachingAgent;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import spock.lang.Subject;

public class TaskCacheClientTest extends CommonCacheClient {
  private final ObjectMapper mapper = new ObjectMapper();
  @Subject private final TaskCacheClient client = new TaskCacheClient(cacheView, mapper);

  @Test
  public void shouldConvert() {
    // Given
    String taskId = "1dc5c17a-422b-4dc4-b493-371970c6c4d6";
    String key = Keys.getTaskKey(ACCOUNT, REGION, taskId);
    String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/test-cluster";
    String taskArn = "arn:aws:ecs:" + REGION + ":012345678910:task/" + taskId;
    String availabilityZone = REGION + "a";

    Task task = new Task();
    task.setClusterArn(clusterArn);
    task.setTaskArn(taskArn);
    task.setContainerInstanceArn(
        "arn:aws:ecs:" + REGION + ":012345678910:container/e09064f7-7361-4c87-8ab9-8d073bbdbcb9");
    task.setGroup("group:testservice-stack-details-v1");
    task.setContainers(Collections.emptyList());
    task.setLastStatus("RUNNING");
    task.setHealthStatus("HEALTHY");
    task.setDesiredStatus("RUNNING");
    task.setStartedAt(new Date());
    task.setAvailabilityZone(availabilityZone);
    Map<String, Object> attributes = TaskCachingAgent.convertTaskToAttributes(task);

    when(cacheView.get(TASKS.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    com.netflix.spinnaker.clouddriver.ecs.cache.model.Task ecsTask = client.get(key);

    // Then
    assertTrue(
        clusterArn.equals(ecsTask.getClusterArn()),
        "Expected the cluster ARN to be " + clusterArn + " but got " + ecsTask.getClusterArn());

    assertTrue(
        taskArn.equals(ecsTask.getTaskArn()),
        "Expected the task ARN to be " + taskArn + " but got " + ecsTask.getTaskArn());

    assertTrue(
        task.getContainerInstanceArn().equals(ecsTask.getContainerInstanceArn()),
        "Expected the container instance ARN name to be "
            + task.getContainerInstanceArn()
            + " but got "
            + ecsTask.getContainerInstanceArn());

    assertTrue(
        task.getGroup().equals(ecsTask.getGroup()),
        "Expected the group to be " + task.getGroup() + " but got " + ecsTask.getGroup());

    assertTrue(
        task.getLastStatus().equals(ecsTask.getLastStatus()),
        "Expected the last status to be "
            + task.getLastStatus()
            + " but got "
            + ecsTask.getLastStatus());

    assertTrue(
        task.getHealthStatus().equals(ecsTask.getHealthStatus()),
        "Expected the health status to be "
            + task.getHealthStatus()
            + " but got "
            + ecsTask.getHealthStatus());

    assertTrue(
        task.getDesiredStatus().equals(ecsTask.getDesiredStatus()),
        "Expected the desired status to be "
            + task.getDesiredStatus()
            + " but got "
            + ecsTask.getDesiredStatus());

    assertTrue(
        task.getStartedAt().getTime() == ecsTask.getStartedAt(),
        "Expected the started at to be "
            + task.getStartedAt().getTime()
            + " but got "
            + ecsTask.getStartedAt());

    assertTrue(
        task.getContainers().size() == 0,
        "Expected the task to have 0 containers but got " + task.getContainers().size());

    assertTrue(
        task.getAvailabilityZone().equals(ecsTask.getAvailabilityZone()),
        "Expected the availability zone to be "
            + task.getAvailabilityZone()
            + " but got "
            + ecsTask.getAvailabilityZone());
  }
}

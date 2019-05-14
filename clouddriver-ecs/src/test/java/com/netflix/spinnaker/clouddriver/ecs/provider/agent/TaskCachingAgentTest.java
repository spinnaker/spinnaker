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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
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

public class TaskCachingAgentTest extends CommonCachingAgent {
  @Subject
  private final TaskCachingAgent agent =
      new TaskCachingAgent(
          netflixAmazonCredentials, REGION, clientProvider, credentialsProvider, registry);

  @Test
  public void shouldGetListOfTasks() {
    // Given
    ListTasksResult listTasksResult = new ListTasksResult().withTaskArns(TASK_ARN_1, TASK_ARN_2);
    when(ecs.listTasks(any(ListTasksRequest.class))).thenReturn(listTasksResult);

    List<Task> tasks = new LinkedList<>();
    tasks.add(new Task().withTaskArn(TASK_ARN_1));
    tasks.add(new Task().withTaskArn(TASK_ARN_2));

    DescribeTasksResult describeResult = new DescribeTasksResult().withTasks(tasks);
    when(ecs.describeTasks(any(DescribeTasksRequest.class))).thenReturn(describeResult);

    when(ecs.listClusters(any(ListClustersRequest.class)))
        .thenReturn(new ListClustersResult().withClusterArns(CLUSTER_ARN_1));

    // When
    List<Task> returnedTasks = agent.getItems(ecs, providerCache);

    // Then
    assertTrue(
        "Expected the list to contain "
            + tasks.size()
            + " ECS tasks, but got "
            + returnedTasks.size(),
        returnedTasks.size() == tasks.size());
    for (Task task : returnedTasks) {
      assertTrue(
          "Expected the task to be in  " + tasks + " list but it was not. The task is: " + task,
          tasks.contains(task));
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    // Given
    List<String> taskIDs = new LinkedList<>();
    taskIDs.add(TASK_ID_1);
    taskIDs.add(TASK_ID_1);

    List<String> taskArns = new LinkedList<>();
    taskArns.add(TASK_ARN_1);
    taskArns.add(TASK_ARN_2);

    List<Task> tasks = new LinkedList<>();
    Set<String> keys = new HashSet<>();
    for (int x = 0; x < taskArns.size(); x++) {
      keys.add(Keys.getTaskKey(ACCOUNT, REGION, taskIDs.get(x)));

      tasks.add(
          new Task()
              .withClusterArn(CLUSTER_ARN_1)
              .withTaskArn(taskArns.get(x))
              .withContainerInstanceArn(CONTAINER_INSTANCE_ARN_1)
              .withGroup("group:" + SERVICE_NAME_1)
              .withContainers(Collections.emptyList())
              .withLastStatus(STATUS)
              .withDesiredStatus(STATUS)
              .withStartedAt(new Date()));
    }

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(tasks);

    // Then
    assertTrue(
        "Expected the data map to contain 2 namespaces, but it contains "
            + dataMap.keySet().size()
            + " namespaces.",
        dataMap.keySet().size() == 2);
    assertTrue(
        "Expected the data map to contain "
            + TASKS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.",
        dataMap.containsKey(TASKS.toString()));
    assertTrue(
        "Expected the data map to contain "
            + ECS_CLUSTERS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.",
        dataMap.containsKey(ECS_CLUSTERS.toString()));
    assertTrue(
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(TASKS.toString()).size(),
        dataMap.get(TASKS.toString()).size() == 2);

    for (CacheData cacheData : dataMap.get(TASKS.toString())) {
      assertTrue(
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".",
          keys.contains(cacheData.getId()));
      assertTrue(
          "Expected the task ARN to be one of the following ARNs: "
              + taskArns.toString()
              + ". The task ARN is: "
              + cacheData.getAttributes().get("taskArn")
              + ".",
          taskArns.contains(cacheData.getAttributes().get("taskArn")));
    }
  }
}

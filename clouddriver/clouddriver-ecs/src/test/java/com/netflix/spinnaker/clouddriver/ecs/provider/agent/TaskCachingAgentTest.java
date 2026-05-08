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

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.cats.provider.DefaultProviderCache;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        returnedTasks.size() == tasks.size(),
        "Expected the list to contain "
            + tasks.size()
            + " ECS tasks, but got "
            + returnedTasks.size());
    for (Task task : returnedTasks) {
      assertTrue(
          tasks.contains(task),
          "Expected the task to be in  " + tasks + " list but it was not. The task is: " + task);
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
        dataMap.keySet().size() == 2,
        "Expected the data map to contain 2 namespaces, but it contains "
            + dataMap.keySet().size()
            + " namespaces.");
    assertTrue(
        dataMap.containsKey(TASKS.toString()),
        "Expected the data map to contain "
            + TASKS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.");
    assertTrue(
        dataMap.containsKey(ECS_CLUSTERS.toString()),
        "Expected the data map to contain "
            + ECS_CLUSTERS.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.");
    assertTrue(
        dataMap.get(TASKS.toString()).size() == 2,
        "Expected there to be 2 CacheData, instead there is  "
            + dataMap.get(TASKS.toString()).size());

    for (CacheData cacheData : dataMap.get(TASKS.toString())) {
      assertTrue(
          keys.contains(cacheData.getId()),
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".");
      assertTrue(
          taskArns.contains(cacheData.getAttributes().get("taskArn")),
          "Expected the task ARN to be one of the following ARNs: "
              + taskArns.toString()
              + ". The task ARN is: "
              + cacheData.getAttributes().get("taskArn")
              + ".");
    }
  }

  /**
   * This demonstrates an issue with the current (soon to be previous) implementation. It loads ALL
   * the cache data for ALL accounts THEN filters it. What's WORSE... is it has a mis-placed
   * paranethesis on the check loading services for ALL REGIONS for ALL ACCOUNTS and only tasks for
   * the specific region/account. This leads to a nasty situation of memroy usage which is much
   * bigger than loading all the data even.
   *
   * <p>What's worse this ALSO puts load on the ServerGroupCacheForceRefreshTask class which does a
   * "model match" on this account/region match. SO it's loading massive data, that's then later
   * filtered by orca, for every account on every load.
   *
   * <p>NOTE: Memory is worse than even performance. We'd see cases of multilple GB of data load due
   * to this incorrectly bursting and processing in bad situations.
   */
  @Test
  @DisplayName(
      "pendingOnDemandRequests should demonstrate performance issues when loading all ON_DEMAND cache entries")
  @Disabled(
      "Used to show some of the performance issues with large data in the account, but not needed now")
  void shouldDemonstratePerformanceIssueWithUnfilteredLoad() {
    // Given: A cache with many unrelated on-demand entries
    DefaultProviderCache testProviderCache = new DefaultProviderCache(new InMemoryCache());

    // Add 10,000 unrelated on-demand entries from other accounts/regions
    for (int i = 0; i < 10000; i++) {
      String otherAccount = "other-account-" + (i % 10);
      String otherRegion = "other-region-" + (i % 5);
      String randomId = RandomStringUtils.randomAlphanumeric(10);

      String key = Keys.getServiceKey(otherAccount, otherRegion, "service-" + randomId);
      Map<String, Object> attrs = new HashMap<>();
      attrs.put("cacheTime", new Date());
      testProviderCache.putCacheData(
          ON_DEMAND.toString(), new DefaultCacheData(key, attrs, Collections.emptyMap()));
    }

    // Add only 10 relevant entries for our account/region
    for (int i = 0; i < 10; i++) {
      String key = Keys.getServiceKey(ACCOUNT, REGION, "service-" + i);
      Map<String, Object> attrs = new HashMap<>();
      attrs.put("cacheTime", new Date());
      testProviderCache.putCacheData(
          ON_DEMAND.toString(), new DefaultCacheData(key, attrs, Collections.emptyMap()));
    }

    // When: Call pendingOnDemandRequests (current implementation loads ALL entries via getAll())
    long startTime = System.currentTimeMillis();
    Collection<Map<String, Object>> results = agent.pendingOnDemandRequests(testProviderCache);
    long duration = System.currentTimeMillis() - startTime;

    // Then: Performance issue demonstrated - we loaded all 10,010 entries and parsed them all
    Collection<CacheData> allLoaded = testProviderCache.getAll(ON_DEMAND.toString());
    assertThat(allLoaded)
        .withFailMessage(
            "Performance issue: getAll() loaded all "
                + allLoaded.size()
                + " entries when only "
                + results.size()
                + " were relevant")
        .hasSize(10010);
    assertThat(results)
        .withFailMessage("Results should have been 10 but were " + results.size())
        .hasSize(10);

    System.out.println(
        "Performance test: results loaded " + results.size() + " entries in " + duration + "ms");
    System.out.println(
        "  - Wasted effort: parsed and filtered "
            + (allLoaded.size() - results.size())
            + " irrelevant entries");
  }

  @Test
  @DisplayName("glob filtering should correctly filter by namespace, account, and region")
  void shouldCorrectlyFilterByGlobPattern() {
    // Given: A cache with entries in different namespaces, accounts, and regions
    DefaultProviderCache testProviderCache = new DefaultProviderCache(new InMemoryCache());

    Map<String, Object> exampleAttrs = Map.of("cacheTime", new Date());
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getServiceKey(ACCOUNT, REGION, "service-1"),
            exampleAttrs,
            Collections.emptyMap()));
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getServiceKey(ACCOUNT, "eu-west-1", "service-2"),
            exampleAttrs,
            Collections.emptyMap()));
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getServiceKey("other-account", REGION, "service-3"),
            exampleAttrs,
            Collections.emptyMap()));
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getTaskKey(ACCOUNT, REGION, "task-1"), exampleAttrs, Collections.emptyMap()));
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getTaskKey(ACCOUNT, "eu-west-1", "task-2"), exampleAttrs, Collections.emptyMap()));
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getTaskKey("other-account", REGION, "task-3"),
            exampleAttrs,
            Collections.emptyMap()));
    testProviderCache.putCacheData(
        ON_DEMAND.toString(),
        new DefaultCacheData(
            Keys.getClusterKey(ACCOUNT, REGION, "cluster-1"),
            exampleAttrs,
            Collections.emptyMap()));

    // When: We want the pending on demand requests for this particular account/region...
    Collection<Map<String, Object>> results = agent.pendingOnDemandRequests(testProviderCache);

    // Then: Should only match the selected on demand entires for this account/region
    assertThat(results)
        .withFailMessage(
            "Incorrect number of results of "
                + results.size()
                + " returned fo the account/region of the provider.  ")
        .hasSize(2);
  }
}

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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

public class TaskCachingAgent extends AbstractEcsOnDemandAgent<Task> {
  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          Arrays.asList(
              AUTHORITATIVE.forType(TASKS.toString()),
              INFORMATIVE.forType(ECS_CLUSTERS.toString())));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public TaskCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      Registry registry) {
    super(account, region, amazonClientProvider, registry);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  protected List<Task> getItems(EcsClient ecs, ProviderCache providerCache) {
    List<Task> taskList = new LinkedList<>();
    Set<String> clusters = getClusters(ecs, providerCache);

    for (String cluster : clusters) {
      String nextToken = null;
      do {
        ListTasksRequest.Builder requestBuilder = ListTasksRequest.builder().cluster(cluster);
        if (nextToken != null) {
          requestBuilder.nextToken(nextToken);
        }
        ListTasksResponse listTasksResult = ecs.listTasks(requestBuilder.build());
        List<String> taskArns = listTasksResult.taskArns();
        if (taskArns.size() == 0) {
          continue;
        }
        DescribeTasksResponse describeTasksResult =
            ecs.describeTasks(
                DescribeTasksRequest.builder().cluster(cluster).tasks(taskArns).build());
        taskList.addAll(describeTasksResult.tasks());
        nextToken = listTasksResult.nextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return taskList;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {

    List<Map<String, Object>> returnResults = new LinkedList<>();

    Collection<String> serviceIds =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.buildGlob(SERVICES.toString(), accountName, region, null));
    Collection<String> taskIds =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.buildGlob(TASKS.toString(), accountName, region, null));
    List<String> combined = new ArrayList<>(serviceIds.size() + taskIds.size());
    combined.addAll(serviceIds);
    combined.addAll(taskIds);

    Collection<CacheData> matchingTheCache = providerCache.getAll(ON_DEMAND.toString(), combined);
    for (CacheData onDemand : matchingTheCache) {
      Map<String, String> parsedKey = Keys.parse(onDemand.getId());
      parsedKey.put("type", "serverGroup");
      parsedKey.put("serverGroup", parsedKey.get("serviceName"));

      HashMap<String, Object> result = new HashMap<>();
      result.put("id", onDemand.getId());
      result.put("details", parsedKey);

      result.put("cacheTime", onDemand.getAttributes().get("cacheTime"));
      result.put("cacheExpiry", onDemand.getAttributes().get("cacheExpiry"));
      result.put(
          "processedCount",
          (onDemand.getAttributes().get("processedCount") != null
              ? onDemand.getAttributes().get("processedCount")
              : 1));
      result.put(
          "processedTime",
          onDemand.getAttributes().get("processedTime") != null
              ? onDemand.getAttributes().get("processedTime")
              : new Date());

      returnResults.add(result);
    }
    return returnResults;
  }

  @Override
  void storeOnDemand(ProviderCache providerCache, Map<String, ?> data) {
    metricsSupport.onDemandStore(
        () -> {
          String keyString =
              Keys.getServiceKey(accountName, region, (String) data.get("serverGroupName"));
          Map<String, Object> att = new HashMap<>();
          att.put("cacheTime", new Date());
          CacheData cacheData = new DefaultCacheData(keyString, att, Collections.emptyMap());
          providerCache.putCacheData(ON_DEMAND.toString(), cacheData);
          return null;
        });
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<Task> tasks) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    Map<String, CacheData> clusterDataPoints = new HashMap<>();

    for (Task task : tasks) {
      String taskId = StringUtils.substringAfterLast(task.taskArn(), "/");
      Map<String, Object> attributes = convertTaskToAttributes(task);

      String key = Keys.getTaskKey(accountName, region, taskId);
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

      String clusterName = StringUtils.substringAfterLast(task.clusterArn(), "/");
      Map<String, Object> clusterAttributes =
          EcsClusterCachingAgent.convertClusterArnToAttributes(
              accountName, region, task.clusterArn());
      key = Keys.getClusterKey(accountName, region, clusterName);
      clusterDataPoints.put(
          key, new DefaultCacheData(key, clusterAttributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " tasks in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASKS.toString(), dataPoints);

    log.info("Caching " + clusterDataPoints.size() + " ECS clusters in " + getAgentType());
    dataMap.put(ECS_CLUSTERS.toString(), clusterDataPoints.values());

    return dataMap;
  }

  public static Map<String, Object> convertTaskToAttributes(Task task) {
    String taskId = StringUtils.substringAfterLast(task.taskArn(), "/");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("taskId", taskId);
    attributes.put("taskArn", task.taskArn());
    attributes.put("clusterArn", task.clusterArn());
    attributes.put("containerInstanceArn", task.containerInstanceArn());
    attributes.put("group", task.group());
    attributes.put("containers", task.containers());
    attributes.put("lastStatus", task.lastStatus());
    attributes.put("desiredStatus", task.desiredStatus());
    attributes.put("healthStatus", task.healthStatusAsString());
    if (task.startedAt() != null) {
      attributes.put("startedAt", task.startedAt().toEpochMilli());
    }
    attributes.put("attachments", task.attachments());
    attributes.put("availabilityZone", task.availabilityZone());

    return attributes;
  }
}

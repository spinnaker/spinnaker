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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

public class TaskCachingAgent extends AbstractEcsOnDemandAgent<Task> {
  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(TASKS.toString()),
    INFORMATIVE.forType(ECS_CLUSTERS.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public TaskCachingAgent(NetflixAmazonCredentials account, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    super(account, region, amazonClientProvider, awsCredentialsProvider, registry);
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
  protected List<Task> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<Task> taskList = new LinkedList<>();
    Set<String> clusters = getClusters(ecs, providerCache);

    for (String cluster : clusters) {
      String nextToken = null;
      do {
        ListTasksRequest listTasksRequest = new ListTasksRequest().withCluster(cluster);
        if (nextToken != null) {
          listTasksRequest.setNextToken(nextToken);
        }
        ListTasksResult listTasksResult = ecs.listTasks(listTasksRequest);
        List<String> taskArns = listTasksResult.getTaskArns();
        if (taskArns.size() == 0) {
          continue;
        }
        List<Task> tasks = ecs.describeTasks(new DescribeTasksRequest().withCluster(cluster).withTasks(taskArns)).getTasks();
        taskList.addAll(tasks);
        nextToken = listTasksResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return taskList;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<CacheData> allOnDemand = providerCache.getAll(ON_DEMAND.toString());
    List<Map> returnResults = new LinkedList<>();
    for (CacheData onDemand : allOnDemand) {
      Map<String, String> parsedKey = Keys.parse(onDemand.getId());
      if (parsedKey != null && parsedKey.get("type") != null &&
        (parsedKey.get("type").equals(SERVICES.toString()) || parsedKey.get("type").equals(TASKS.toString()) &&
          parsedKey.get("account").equals(accountName) && parsedKey.get("region").equals(region))) {

        parsedKey.put("type", "serverGroup");
        parsedKey.put("serverGroup", parsedKey.get("serviceName"));

        HashMap<String, Object> result = new HashMap<>();
        result.put("id", onDemand.getId());
        result.put("details", parsedKey);

        result.put("cacheTime", onDemand.getAttributes().get("cacheTime"));
        result.put("cacheExpiry", onDemand.getAttributes().get("cacheExpiry"));
        result.put("processedCount", (onDemand.getAttributes().get("processedCount") != null ? onDemand.getAttributes().get("processedCount") : 1));
        result.put("processedTime", onDemand.getAttributes().get("processedTime") != null ? onDemand.getAttributes().get("processedTime") : new Date());

        returnResults.add(result);
      }
    }
    return returnResults;
  }

  @Override
  void storeOnDemand(ProviderCache providerCache, Map<String, ?> data) {
    metricsSupport.onDemandStore(() ->{
        String keyString = Keys.getServiceKey(accountName, region, (String) data.get("serverGroupName"));
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
      String taskId = StringUtils.substringAfterLast(task.getTaskArn(), "/");
      Map<String, Object> attributes = convertTaskToAttributes(task);

      String key = Keys.getTaskKey(accountName, region, taskId);
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

      String clusterName = StringUtils.substringAfterLast(task.getClusterArn(), "/");
      Map<String, Object> clusterAttributes = EcsClusterCachingAgent.convertClusterArnToAttributes(accountName, region, task.getClusterArn());
      key = Keys.getClusterKey(accountName, region, clusterName);
      clusterDataPoints.put(key, new DefaultCacheData(key, clusterAttributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " tasks in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASKS.toString(), dataPoints);

    log.info("Caching " + clusterDataPoints.size() + " ECS clusters in " + getAgentType());
    dataMap.put(ECS_CLUSTERS.toString(), clusterDataPoints.values());

    return dataMap;
  }

  public static Map<String, Object> convertTaskToAttributes(Task task) {
    String taskId = StringUtils.substringAfterLast(task.getTaskArn(), "/");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("taskId", taskId);
    attributes.put("taskArn", task.getTaskArn());
    attributes.put("clusterArn", task.getClusterArn());
    attributes.put("containerInstanceArn", task.getContainerInstanceArn());
    attributes.put("group", task.getGroup());
    attributes.put("containers", task.getContainers());
    attributes.put("lastStatus", task.getLastStatus());
    attributes.put("desiredStatus", task.getDesiredStatus());
    attributes.put("startedAt", task.getStartedAt().getTime());
    attributes.put("attachments", task.getAttachments());

    return attributes;
  }
}

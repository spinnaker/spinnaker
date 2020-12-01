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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDefinitionCachingAgent extends AbstractEcsOnDemandAgent<TaskDefinition> {
  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          Arrays.asList(AUTHORITATIVE.forType(TASK_DEFINITIONS.toString())));
  private final Logger log = LoggerFactory.getLogger(getClass());

  private ObjectMapper objectMapper;

  public TaskDefinitionCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry,
      ObjectMapper objectMapper) {
    super(account, region, amazonClientProvider, awsCredentialsProvider, registry);
    this.objectMapper = objectMapper;
  }

  public static Map<String, Object> convertTaskDefinitionToAttributes(
      TaskDefinition taskDefinition) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("taskDefinitionArn", taskDefinition.getTaskDefinitionArn());
    attributes.put("containerDefinitions", taskDefinition.getContainerDefinitions());
    attributes.put("taskRoleArn", taskDefinition.getTaskRoleArn());
    attributes.put("memory", taskDefinition.getMemory());
    attributes.put("cpu", taskDefinition.getCpu());
    return attributes;
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
  protected List<TaskDefinition> getItems(AmazonECS ecs, ProviderCache providerCache) {
    // get all ECS services in region for account
    ServiceCacheClient serviceCacheClient = new ServiceCacheClient(providerCache, objectMapper);
    Collection<Service> services = serviceCacheClient.getAll(accountName, region);
    log.debug("Found {} ECS services for which to cache task definitions", services.size());

    Set<String> taskDefArns = new HashSet<>();

    for (Service service : services) {
      taskDefArns.add(service.getTaskDefinition());
    }

    List<TaskDefinition> taskDefinitions = new ArrayList<>();

    int newTaskDefs = 0;

    for (String arn : taskDefArns) {

      // TaskDefinitions are immutable, there's no reason to
      // make a describe call on existing ones.
      TaskDefinition cacheEntry = retrieveFromCache(arn, providerCache);

      if (cacheEntry != null) {
        taskDefinitions.add(cacheEntry);
      } else {
        TaskDefinition taskDef =
            ecs.describeTaskDefinition(new DescribeTaskDefinitionRequest().withTaskDefinition(arn))
                .getTaskDefinition();
        if (taskDef != null) {
          taskDefinitions.add(taskDef);
          newTaskDefs++;
        }
      }
    }

    log.info(
        "Described {} new task definitions ({} already cached)",
        newTaskDefs,
        taskDefinitions.size() - newTaskDefs);

    return taskDefinitions;
  }

  private TaskDefinition retrieveFromCache(String taskDefArn, ProviderCache providerCache) {
    TaskDefinitionCacheClient taskDefinitionCacheClient =
        new TaskDefinitionCacheClient(providerCache, objectMapper);

    String key = Keys.getTaskDefinitionKey(accountName, region, taskDefArn);

    return taskDefinitionCacheClient.get(key);
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(
      Collection<TaskDefinition> taskDefinitions) {
    Collection<CacheData> dataPoints = new LinkedList<>();

    for (TaskDefinition taskDefinition : taskDefinitions) {
      Map<String, Object> attributes = convertTaskDefinitionToAttributes(taskDefinition);
      String key =
          Keys.getTaskDefinitionKey(accountName, region, taskDefinition.getTaskDefinitionArn());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " task definitions in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASK_DEFINITIONS.toString(), dataPoints);

    return dataMap;
  }
}

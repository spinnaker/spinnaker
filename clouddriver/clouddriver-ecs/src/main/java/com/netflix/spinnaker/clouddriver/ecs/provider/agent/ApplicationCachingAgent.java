/*
 * Copyright 2021 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_APPLICATIONS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Application;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationCachingAgent extends AbstractEcsOnDemandAgent<Application> {
  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          Arrays.asList(AUTHORITATIVE.forType(ECS_APPLICATIONS.toString())));
  private final Logger log = LoggerFactory.getLogger(getClass());

  private ObjectMapper objectMapper;

  public ApplicationCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry,
      ObjectMapper objectMapper) {
    super(account, region, amazonClientProvider, awsCredentialsProvider, registry);
    this.objectMapper = objectMapper;
  }

  public static Map<String, Object> convertApplicationToAttributes(Application application) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("name", application.getName());
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
  protected List<Application> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<Application> applications = new ArrayList<>();
    return applications;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(
      Collection<Application> applications) {
    Collection<CacheData> applicationData = new LinkedList<>();

    Map<String, Collection<CacheData>> cacheDataMap = new HashMap<>();
    log.info("Amazon ECS ApplicationCachingAgent will cache applications in a future update");
    cacheDataMap.put(ECS_APPLICATIONS.toString(), applicationData);

    return cacheDataMap;
  }
}

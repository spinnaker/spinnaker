/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Application;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationCachingAgent extends AbstractEcsOnDemandAgent<Application> {
  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          Arrays.asList(AUTHORITATIVE.forType(APPLICATIONS.toString())));
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
    // get all ECS services
    ServiceCacheClient serviceCacheClient = new ServiceCacheClient(providerCache, objectMapper);
    Collection<Service> services = serviceCacheClient.getAll();
    log.info("Found {} ECS services for which to cache applications", services.size());

    Map<String, Map<String, Collection<String>>> appRelationships = new HashMap<>();

    for (Service service : services) {
      String applicationKey = service.getApplicationName();
      String serviceKey =
          Keys.getServiceKey(service.getAccount(), service.getRegion(), service.getServiceName());

      appRelationships.put(
          applicationKey,
          updateApplicationRelationships(appRelationships, applicationKey, serviceKey)
              .get(applicationKey));

      log.debug(
          "ECS application "
              + applicationKey
              + " with "
              + appRelationships.get(applicationKey).size()
              + " relationships");
    }

    List<Application> applications = new ArrayList<>();

    for (Map.Entry<String, Map<String, Collection<String>>> appInfo : appRelationships.entrySet()) {
      Application application = new Application();
      application.setName(appInfo.getKey());
      application.setRelationships(appInfo.getValue());

      applications.add(application);
    }

    log.info("Cached {} applications for {} services", applications.size(), services.size());
    return applications;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(
      Collection<Application> applications) {
    Collection<CacheData> applicationData = new LinkedList<>();

    for (Application application : applications) {
      Map<String, Object> attributes = convertApplicationToAttributes(application);
      String applicationKey = Keys.getApplicationKey(application.getName());

      applicationData.add(
          new DefaultCacheData(applicationKey, attributes, application.getRelationships()));
    }

    Map<String, Collection<CacheData>> cacheDataMap = new HashMap<>();
    log.info("Caching " + applicationData.size() + " ECS applications in " + getAgentType());
    cacheDataMap.put(APPLICATIONS.toString(), applicationData);

    return cacheDataMap;
  }

  private Map<String, Map<String, Collection<String>>> updateApplicationRelationships(
      Map<String, Map<String, Collection<String>>> appRelationships,
      String applicationKey,
      String serviceKey) {
    Map<String, Collection<String>> existingAppRelation = appRelationships.get(applicationKey);
    if (existingAppRelation != null && existingAppRelation.size() > 0) {
      log.debug("Updating existing application relation for " + applicationKey);

      Collection<String> serviceRelationship = existingAppRelation.get(SERVICES.ns);
      if (serviceRelationship != null && !serviceRelationship.isEmpty()) {
        serviceRelationship.add(serviceKey);
      } else {
        serviceRelationship = Sets.newHashSet(serviceKey);
      }
      existingAppRelation.put(SERVICES.ns, serviceRelationship);
    } else {
      log.debug("Creating new application relation for " + applicationKey);

      existingAppRelation = new HashMap<String, Collection<String>>();
      existingAppRelation.put(SERVICES.ns, Sets.newHashSet(serviceKey));
    }

    appRelationships.put(applicationKey, existingAppRelation);
    return appRelationships;
  }
}

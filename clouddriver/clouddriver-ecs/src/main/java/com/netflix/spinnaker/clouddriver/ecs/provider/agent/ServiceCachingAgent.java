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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResource;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResourceService;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;

public class ServiceCachingAgent extends AbstractEcsOnDemandAgent<Service> {
  private static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          Arrays.asList(
              AUTHORITATIVE.forType(SERVICES.toString()),
              INFORMATIVE.forType(ECS_CLUSTERS.toString())));
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Namer<EcsResource> naming;

  public ServiceCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      Registry registry) {
    this(
        account,
        region,
        amazonClientProvider,
        registry,
        NamerRegistry.lookup()
            .withProvider(EcsCloudProvider.ID)
            .withAccount(account.getName())
            .withResource(EcsResource.class));
  }

  @VisibleForTesting
  public ServiceCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      Registry registry,
      Namer naming) {
    super(account, region, amazonClientProvider, registry);
    this.naming = naming;
  }

  public Map<String, Object> convertServiceToAttributes(Service service) {
    Map<String, Object> attributes = new HashMap<>();

    Moniker moniker = naming.deriveMoniker(new EcsResourceService(service));

    String applicationName = moniker.getApp();
    String clusterName = StringUtils.substringAfterLast(service.clusterArn(), "/");

    attributes.put("account", accountName);
    attributes.put("region", region);
    attributes.put("applicationName", applicationName);
    attributes.put("serviceName", service.serviceName());
    attributes.put("serviceArn", service.serviceArn());
    attributes.put("clusterName", clusterName);
    attributes.put("clusterArn", service.clusterArn());
    attributes.put("roleArn", service.roleArn());
    attributes.put("taskDefinition", service.taskDefinition());
    attributes.put("desiredCount", service.desiredCount());
    attributes.put("maximumPercent", service.deploymentConfiguration().maximumPercent());
    attributes.put(
        "minimumHealthyPercent", service.deploymentConfiguration().minimumHealthyPercent());
    attributes.put("loadBalancers", service.loadBalancers());

    if (service.networkConfiguration() != null
        && service.networkConfiguration().awsvpcConfiguration() != null) {
      attributes.put("subnets", service.networkConfiguration().awsvpcConfiguration().subnets());
      attributes.put(
          "securityGroups", service.networkConfiguration().awsvpcConfiguration().securityGroups());
    }

    attributes.put("createdAt", service.createdAt().toEpochMilli());
    attributes.put("moniker", moniker);

    return attributes;
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  protected List<Service> getItems(EcsClient ecs, ProviderCache providerCache) {
    List<Service> serviceList = new LinkedList<>();
    Set<String> clusters = getClusters(ecs, providerCache);

    for (String cluster : clusters) {
      String nextToken = null;
      do {
        ListServicesRequest.Builder requestBuilder = ListServicesRequest.builder().cluster(cluster);
        if (nextToken != null) {
          requestBuilder.nextToken(nextToken);
        }
        ListServicesResponse listServicesResult = ecs.listServices(requestBuilder.build());
        List<String> serviceArns = listServicesResult.serviceArns();
        if (serviceArns.size() == 0) {
          continue;
        }

        DescribeServicesResponse describeServicesResult =
            ecs.describeServices(
                DescribeServicesRequest.builder()
                    .cluster(cluster)
                    .services(serviceArns)
                    .includeWithStrings("TAGS")
                    .build());
        serviceList.addAll(describeServicesResult.services());

        nextToken = listServicesResult.nextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return serviceList;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<Service> services) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    Map<String, CacheData> clusterDataPoints = new HashMap<>();

    for (Service service : services) {
      Map<String, Object> attributes = convertServiceToAttributes(service);

      String key = Keys.getServiceKey(accountName, region, service.serviceName());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

      Map<String, Object> clusterAttributes =
          EcsClusterCachingAgent.convertClusterArnToAttributes(
              accountName, region, service.clusterArn());
      String clusterName = StringUtils.substringAfterLast(service.clusterArn(), "/");
      key = Keys.getClusterKey(accountName, region, clusterName);
      clusterDataPoints.put(
          key, new DefaultCacheData(key, clusterAttributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " services in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(SERVICES.toString(), dataPoints);

    log.info("Caching " + clusterDataPoints.size() + " ECS clusters in " + getAgentType());
    dataMap.put(ECS_CLUSTERS.toString(), clusterDataPoints.values());

    return dataMap;
  }
}

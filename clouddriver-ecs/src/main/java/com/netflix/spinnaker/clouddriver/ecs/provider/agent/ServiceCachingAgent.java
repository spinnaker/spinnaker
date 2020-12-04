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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
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
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry) {
    this(
        account,
        region,
        amazonClientProvider,
        awsCredentialsProvider,
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
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry,
      Namer naming) {
    super(account, region, amazonClientProvider, awsCredentialsProvider, registry);
    this.naming = naming;
  }

  public Map<String, Object> convertServiceToAttributes(Service service) {
    Map<String, Object> attributes = new HashMap<>();

    Moniker moniker = naming.deriveMoniker(new EcsResourceService(service));

    String applicationName = moniker.getApp();
    String clusterName = StringUtils.substringAfterLast(service.getClusterArn(), "/");

    attributes.put("account", accountName);
    attributes.put("region", region);
    attributes.put("applicationName", applicationName);
    attributes.put("serviceName", service.getServiceName());
    attributes.put("serviceArn", service.getServiceArn());
    attributes.put("clusterName", clusterName);
    attributes.put("clusterArn", service.getClusterArn());
    attributes.put("roleArn", service.getRoleArn());
    attributes.put("taskDefinition", service.getTaskDefinition());
    attributes.put("desiredCount", service.getDesiredCount());
    attributes.put("maximumPercent", service.getDeploymentConfiguration().getMaximumPercent());
    attributes.put(
        "minimumHealthyPercent", service.getDeploymentConfiguration().getMinimumHealthyPercent());
    attributes.put("loadBalancers", service.getLoadBalancers());

    if (service.getNetworkConfiguration() != null
        && service.getNetworkConfiguration().getAwsvpcConfiguration() != null) {
      attributes.put(
          "subnets", service.getNetworkConfiguration().getAwsvpcConfiguration().getSubnets());
      attributes.put(
          "securityGroups",
          service.getNetworkConfiguration().getAwsvpcConfiguration().getSecurityGroups());
    }

    attributes.put("createdAt", service.getCreatedAt().getTime());
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
  protected List<Service> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<Service> serviceList = new LinkedList<>();
    Set<String> clusters = getClusters(ecs, providerCache);

    for (String cluster : clusters) {
      String nextToken = null;
      do {
        ListServicesRequest listServicesRequest = new ListServicesRequest().withCluster(cluster);
        if (nextToken != null) {
          listServicesRequest.setNextToken(nextToken);
        }
        ListServicesResult listServicesResult = ecs.listServices(listServicesRequest);
        List<String> serviceArns = listServicesResult.getServiceArns();
        if (serviceArns.size() == 0) {
          continue;
        }

        List<Service> services =
            ecs.describeServices(
                    new DescribeServicesRequest()
                        .withCluster(cluster)
                        .withServices(serviceArns)
                        .withInclude("TAGS"))
                .getServices();
        serviceList.addAll(services);

        nextToken = listServicesResult.getNextToken();
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

      String key = Keys.getServiceKey(accountName, region, service.getServiceName());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));

      Map<String, Object> clusterAttributes =
          EcsClusterCachingAgent.convertClusterArnToAttributes(
              accountName, region, service.getClusterArn());
      String clusterName = StringUtils.substringAfterLast(service.getClusterArn(), "/");
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

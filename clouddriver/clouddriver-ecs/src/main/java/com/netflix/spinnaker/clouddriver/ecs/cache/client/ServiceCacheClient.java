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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;

@Component
public class ServiceCacheClient extends AbstractCacheClient<Service> {
  private ObjectMapper objectMapper;
  private final boolean queryByApplication;

  public ServiceCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    this(cacheView, objectMapper, false);
  }

  /**
   * @param queryByApplication enables {@link #findByApplication}. Services only carry the indexed
   *     application once their caching agent has re-cached them, so this should only be enabled
   *     after the agents have completed a cycle with the application attribute.
   */
  @Autowired
  public ServiceCacheClient(
      Cache cacheView,
      ObjectMapper objectMapper,
      @Value("${ecs.cache.query-by-application:false}") boolean queryByApplication) {
    super(cacheView, SERVICES.toString());
    this.objectMapper = objectMapper;
    this.queryByApplication = queryByApplication;
  }

  /** Whether {@link #findByApplication} may be used: enabled by config and backed by an index. */
  public boolean canFindByApplication() {
    return queryByApplication && cacheView.supportsGetAllByApplication();
  }

  /**
   * Finds the services of an application across all accounts and regions through the cache's
   * application index. Unlike a service name glob this also finds services whose application comes
   * from tags. Callers must check {@link #canFindByApplication()} first.
   */
  public Collection<Service> findByApplication(String application) {
    Map<String, Collection<CacheData>> byType =
        cacheView.getAllByApplication(SERVICES.toString(), application);
    Collection<CacheData> data = byType == null ? null : byType.get(SERVICES.toString());
    if (data == null) {
      return Collections.emptyList();
    }
    return data.stream().map(this::convert).collect(Collectors.toList());
  }

  @Override
  protected Service convert(CacheData cacheData) {
    Service service = new Service();
    Map<String, Object> attributes = cacheData.getAttributes();

    service.setAccount((String) attributes.get("account"));
    service.setRegion((String) attributes.get("region"));
    service.setApplicationName((String) attributes.get("applicationName"));
    service.setServiceName((String) attributes.get("serviceName"));
    service.setServiceArn((String) attributes.get("serviceArn"));
    service.setClusterName((String) attributes.get("clusterName"));
    service.setClusterArn((String) attributes.get("clusterArn"));
    service.setRoleArn((String) attributes.get("roleArn"));
    service.setTaskDefinition((String) attributes.get("taskDefinition"));
    service.setDesiredCount((Integer) attributes.get("desiredCount"));
    service.setMaximumPercent((Integer) attributes.get("maximumPercent"));
    service.setMinimumHealthyPercent((Integer) attributes.get("minimumHealthyPercent"));
    service.setSubnets((List<String>) attributes.get("subnets"));
    service.setSecurityGroups((List<String>) attributes.get("securityGroups"));

    if (attributes.containsKey("loadBalancers")) {
      List<Map<String, Object>> loadBalancers =
          (List<Map<String, Object>>) attributes.get("loadBalancers");
      List<LoadBalancer> deserializedLoadbalancers = new ArrayList<>(loadBalancers.size());

      for (Map<String, Object> serializedLoadbalancer : loadBalancers) {
        if (serializedLoadbalancer != null) {
          deserializedLoadbalancers.add(
              objectMapper.convertValue(serializedLoadbalancer, LoadBalancer.class));
        }
      }

      service.setLoadBalancers(deserializedLoadbalancers);
    } else {
      service.setLoadBalancers(Collections.emptyList());
    }

    service.setCreatedAt((Long) attributes.get("createdAt"));

    service.setDeploymentId((String) attributes.get("deploymentId"));
    service.setRolloutState((String) attributes.get("rolloutState"));
    service.setRolloutStateReason((String) attributes.get("rolloutStateReason"));
    service.setEcsNative(Boolean.TRUE.equals(attributes.get("ecsNative")));

    if (attributes.containsKey("moniker")) {
      service.setMoniker(objectMapper.convertValue(attributes.get("moniker"), Moniker.class));
    }

    return service;
  }
}

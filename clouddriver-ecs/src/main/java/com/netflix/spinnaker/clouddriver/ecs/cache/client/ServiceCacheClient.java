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

import com.amazonaws.services.ecs.model.LoadBalancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

@Component
public class ServiceCacheClient extends AbstractCacheClient<Service> {
  private ObjectMapper objectMapper;

  @Autowired
  public ServiceCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    super(cacheView, SERVICES.toString());
    this.objectMapper = objectMapper;
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
    service.setSubnets((List<String>)attributes.get("subnets"));
    service.setSecurityGroups((List<String>)attributes.get("securityGroups"));

    if (attributes.containsKey("loadBalancers")) {
      List<Map<String, Object>> loadBalancers = (List<Map<String, Object>>) attributes.get("loadBalancers");
      List<LoadBalancer> deserializedLoadbalancers = new ArrayList<>(loadBalancers.size());

      for (Map<String, Object> serializedLoadbalancer : loadBalancers) {
        if (serializedLoadbalancer != null) {
          deserializedLoadbalancers.add(objectMapper.convertValue(serializedLoadbalancer, LoadBalancer.class));
        }
      }

      service.setLoadBalancers(deserializedLoadbalancers);
    } else {
      service.setLoadBalancers(Collections.emptyList());
    }


    service.setCreatedAt((Long) attributes.get("createdAt"));

    return service;
  }
}

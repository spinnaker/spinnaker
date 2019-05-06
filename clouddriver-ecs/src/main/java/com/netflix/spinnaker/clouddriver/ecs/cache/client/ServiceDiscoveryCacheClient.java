/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ServiceDiscoveryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICE_DISCOVERY_REGISTRIES;

@Component
public class ServiceDiscoveryCacheClient extends AbstractCacheClient<ServiceDiscoveryRegistry>{

  @Autowired
  public ServiceDiscoveryCacheClient(Cache cacheView) {
    super(cacheView, SERVICE_DISCOVERY_REGISTRIES.toString());
  }

  @Override
  protected ServiceDiscoveryRegistry convert(CacheData cacheData) {
    ServiceDiscoveryRegistry registry = new ServiceDiscoveryRegistry();
    Map<String, Object> attributes = cacheData.getAttributes();

    registry.setAccount((String) attributes.get("account"));
    registry.setRegion((String) attributes.get("region"));
    registry.setName((String) attributes.get("serviceName"));
    registry.setArn((String) attributes.get("serviceArn"));
    registry.setId((String) attributes.get("serviceId"));

    return registry;
  }
}

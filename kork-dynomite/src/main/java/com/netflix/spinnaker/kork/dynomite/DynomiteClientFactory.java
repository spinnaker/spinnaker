/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.dynomite;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.jedis.DynoJedisClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;

public class DynomiteClientFactory {

  private final Logger log = LoggerFactory.getLogger(DynomiteClientFactory.class);

  private DynomiteDriverProperties properties;
  private Optional<DiscoveryClient> discoveryClient;

  public DynomiteClientFactory properties(DynomiteDriverProperties properties) {
    this.properties = properties;
    return this;
  }

  public DynomiteClientFactory discoveryClient(Optional<DiscoveryClient> discoveryClient) {
    this.discoveryClient = discoveryClient;
    return this;
  }

  public DynoJedisClient build() {
    DynoJedisClient.Builder builder =
        new DynoJedisClient.Builder()
            .withApplicationName(properties.applicationName)
            .withDynomiteClusterName(properties.clusterName);

    if (properties.connectionPool == null || !"{}".equals(properties.connectionPool.getHashtag())) {
      // I don't really want to make the assumption all of our services will use hashtags, but they
      // probably will...
      log.warn("Hashtag value has not been set. This will likely lead to inconsistent operations.");
    }

    Optional<DiscoveryClient> discovery = getDiscoveryClient();
    if (discovery.isPresent()) {
      builder.withDiscoveryClient(discovery.get());
    } else {
      if (properties.hosts.isEmpty()) {
        throw new BeanCreationException(
            "Dynomite hosts must be set if discovery info not provided");
      }
      properties
          .connectionPool
          .withTokenSupplier(new StaticTokenMapSupplier(properties.getDynoHostTokens()))
          .setLocalDataCenter(properties.localDatacenter)
          .setLocalRack(properties.localRack);

      builder.withHostSupplier(new StaticHostSupplier(properties.getDynoHosts()));
    }

    builder.withCPConfig(properties.connectionPool);

    return builder.build();
  }

  private Optional<DiscoveryClient> getDiscoveryClient() {
    if (properties.forceUseStaticHosts) {
      return Optional.empty();
    }
    return discoveryClient;
  }
}

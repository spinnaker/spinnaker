/*
 * Copyright 2020 Netflix, Inc.
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
 */
package com.netflix.spinnaker.front50.model.plugins;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Objects;
import rx.Scheduler;

public class DefaultPluginVersionPinningRepository
    extends StorageServiceSupport<ServerGroupPluginVersions>
    implements PluginVersionPinningRepository {
  public DefaultPluginVersionPinningRepository(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      StorageServiceConfigurationProperties.PerObjectType configurationProperties,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    super(
        ObjectType.PLUGIN_VERSIONS,
        service,
        scheduler,
        objectKeyLoader,
        configurationProperties,
        registry,
        circuitBreakerRegistry);
  }

  @Override
  public ServerGroupPluginVersions create(String id, ServerGroupPluginVersions item) {
    Objects.requireNonNull(item.getId());
    Objects.requireNonNull(item.getPluginVersions());
    if (!item.getId().equals(id)) {
      throw new IntegrationException("The provided id and body id do not match");
    }

    if (item.getCreateTs() == null) {
      item.setCreateTs(System.currentTimeMillis());
    }
    item.setLastModified(System.currentTimeMillis());

    update(id, item);
    return findById(id);
  }
}

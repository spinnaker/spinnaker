/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import rx.Scheduler;

public class DefaultPluginInfoRepository extends StorageServiceSupport<PluginInfo>
    implements PluginInfoRepository {
  public DefaultPluginInfoRepository(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      long refreshIntervalMs,
      boolean shouldWarmCache,
      Registry registry) {
    super(
        ObjectType.PLUGIN_INFO,
        service,
        scheduler,
        objectKeyLoader,
        refreshIntervalMs,
        shouldWarmCache,
        registry);
  }

  @Nonnull
  @Override
  public Collection<PluginInfo> getByService(@Nonnull String service) {
    return all().stream()
        .filter(info -> info.getReleases().stream().anyMatch(r -> r.supportsService(service)))
        .collect(Collectors.toList());
  }

  @Override
  public PluginInfo create(String id, PluginInfo item) {
    Objects.requireNonNull(item.getId());
    if (!item.getId().equals(id)) {
      // Won't happen unless Orca passes a mismatched request path / request body.
      throw new IntegrationException("The provided id and plugin info id do not match");
    }

    if (item.getCreateTs() == null) {
      item.setCreateTs(System.currentTimeMillis());
    } else {
      item.setLastModified(System.currentTimeMillis());
    }

    update(id, item);
    return findById(id);
  }
}

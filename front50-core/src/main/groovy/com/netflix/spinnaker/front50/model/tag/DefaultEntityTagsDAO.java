/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model.tag;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import rx.Scheduler;

public class DefaultEntityTagsDAO extends StorageServiceSupport<EntityTags>
    implements EntityTagsDAO {
  public DefaultEntityTagsDAO(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      long refreshIntervalMs,
      boolean shouldWarmCache,
      Registry registry) {
    super(
        ObjectType.ENTITY_TAGS,
        service,
        scheduler,
        objectKeyLoader,
        refreshIntervalMs,
        shouldWarmCache,
        registry);
  }

  @Override
  public EntityTags create(String id, EntityTags tag) {
    return upsert(id, tag);
  }

  @Override
  public void update(String id, EntityTags tag) {
    upsert(id, tag);
  }

  private EntityTags upsert(String id, EntityTags tag) {
    Objects.requireNonNull(id);
    super.update(id, tag);
    return findById(id);
  }

  @Override
  public boolean isHealthy() {
    return true;
  }

  @Override
  protected long getHealthMillis() {
    return TimeUnit.MINUTES.toMillis(15);
  }
}

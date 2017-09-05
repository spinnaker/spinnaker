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

package com.netflix.spinnaker.front50.model.snapshot;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import org.springframework.util.Assert;
import rx.Scheduler;

import java.util.Collection;

public class DefaultSnapshotDAO extends StorageServiceSupport<Snapshot> implements SnapshotDAO {
  public DefaultSnapshotDAO(StorageService service,
                            Scheduler scheduler,
                            ObjectKeyLoader objectKeyLoader,
                            long refreshIntervalMs,
                            boolean shouldWarmCache,
                            Registry registry) {
    super(ObjectType.SNAPSHOT, service, scheduler, objectKeyLoader, refreshIntervalMs, shouldWarmCache, registry);
  }

  @Override
  public Snapshot create(String id, Snapshot item) {
    Assert.notNull(item.getApplication(), "application field must NOT be null!");
    Assert.notNull(item.getAccount(), "account field must NOT be null!");
    if (id == null) {
      id = item.getApplication() + "-" + item.getAccount();
    }
    item.setId(id);
    item.setTimestamp(System.currentTimeMillis());

    super.update(id, item);
    return findById(id);
  }

}

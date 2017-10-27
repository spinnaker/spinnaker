/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.front50.model.intent;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import org.springframework.util.Assert;
import rx.Scheduler;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultIntentDAO extends StorageServiceSupport<Intent> implements IntentDAO {

  public DefaultIntentDAO(StorageService service,
                                    Scheduler scheduler,
                                    ObjectKeyLoader objectKeyLoader,
                                    long refreshIntervalMs,
                                    boolean shouldWarmCache,
                                    Registry registry) {
    super(ObjectType.INTENT, service, scheduler, objectKeyLoader, refreshIntervalMs, shouldWarmCache, registry);
  }

  @Override
  public Collection<Intent> getIntentsByKind(List<String> kind) {
    return all()
      .stream()
      .filter(i -> kind.contains(i.getKind()))
      .collect(Collectors.toList());
  }

  @Override
  public Collection<Intent> getIntentsByStatus(List<String> status) {
    if (status == null || status.isEmpty()) {
      return all();
    }

    return all()
      .stream()
      .filter(i -> status.contains(i.getStatus()))
      .collect(Collectors.toList());
  }

  @Override
  public Intent create(String id, Intent item) {
    Assert.notNull(item.getId(), "id field must NOT to be null!");
    Assert.notNull(item.getSchema(), "schema field must NOT to be null!");
    Assert.notNull(item.getSpec(), "spec field must NOT to be null!");
    Assert.notNull(item.getKind(), "kind field must NOT to be null!");
    if (item.getStatus() == null) {
      item.setStatus("ACTIVE");
    }

    update(id, item);
    return findById(id);
  }
}

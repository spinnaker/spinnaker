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

package com.netflix.spinnaker.front50.model.serviceaccount;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import rx.Scheduler;

public class DefaultServiceAccountDAO extends StorageServiceSupport<ServiceAccount>
    implements ServiceAccountDAO {
  public DefaultServiceAccountDAO(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      long refreshIntervalMs,
      boolean shouldWarmCache,
      Registry registry) {
    super(
        ObjectType.SERVICE_ACCOUNT,
        service,
        scheduler,
        objectKeyLoader,
        refreshIntervalMs,
        shouldWarmCache,
        registry);
  }

  @Override
  public ServiceAccount create(String id, ServiceAccount permission) {
    return upsert(id, permission);
  }

  @Override
  public void update(String id, ServiceAccount permission) {
    upsert(id, permission);
  }

  private ServiceAccount upsert(String id, ServiceAccount permission) {
    permission.setName(id);
    super.update(id, permission);
    return findById(id);
  }
}

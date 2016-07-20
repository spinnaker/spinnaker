/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.front50.model;

import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import rx.Scheduler;

public class ServiceAccountBucketDAO extends BucketDAO<ServiceAccount> implements ServiceAccountDAO {

  public ServiceAccountBucketDAO(String basePath,
                                 StorageService service,
                                 Scheduler scheduler,
                                 int refreshIntervalMs) {
    super(ServiceAccount.class, "serviceAccounts", basePath, service, scheduler, refreshIntervalMs);
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
    permission.setLastModified(System.currentTimeMillis());
    super.update(id, permission);
    return findById(id);
  }
}

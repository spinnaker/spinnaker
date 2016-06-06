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

package com.netflix.spinnaker.front50.model;

import com.google.api.services.storage.Storage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel;
import com.netflix.spinnaker.front50.model.notification.Notification;
import com.netflix.spinnaker.front50.model.notification.NotificationDAO;
import rx.Scheduler;

public class NotificationBucketDAO extends BucketDAO<Notification> implements NotificationDAO {
  public NotificationBucketDAO(String basePath,
                               StorageService service,
                               Scheduler scheduler,
                               int refreshIntervalMs) {
      super(Notification.class, "notifications",
            basePath, service, scheduler, refreshIntervalMs);
  }

  @Override
  public Notification getGlobal() {
    return get(HierarchicalLevel.GLOBAL, Notification.GLOBAL_ID);
  }

  @Override
  public Notification get(HierarchicalLevel level, String name) {
    try {
      return findById(name);
    } catch (NotFoundException e) {
      // an empty Notification is expected for applications that do not exist
      return new Notification();
    }
  }

  @Override
  public void saveGlobal(Notification notification) {
    update(Notification.GLOBAL_ID, notification);
  }

  @Override
  public void save(HierarchicalLevel level, String name, Notification notification) {
    update(name, notification);
  }

  @Override
  public void delete(HierarchicalLevel level, String name) {
    delete(name);
  }
}

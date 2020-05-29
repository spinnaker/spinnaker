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

package com.netflix.spinnaker.front50.model.notification;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.StorageServiceSupport;
import rx.Scheduler;

public class DefaultNotificationDAO extends StorageServiceSupport<Notification>
    implements NotificationDAO {
  public DefaultNotificationDAO(
      StorageService service,
      Scheduler scheduler,
      ObjectKeyLoader objectKeyLoader,
      long refreshIntervalMs,
      boolean shouldWarmCache,
      Registry registry) {
    super(
        ObjectType.NOTIFICATION,
        service,
        scheduler,
        objectKeyLoader,
        refreshIntervalMs,
        shouldWarmCache,
        registry);
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

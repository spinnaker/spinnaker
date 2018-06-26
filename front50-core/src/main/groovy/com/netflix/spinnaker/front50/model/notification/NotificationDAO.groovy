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


package com.netflix.spinnaker.front50.model.notification

interface NotificationDAO {
  static final Collection<String> NOTIFICATION_FORMATS = ['sms', 'email', 'hipchat', 'slack', 'bearychat', 'googlechat']

  Collection<Notification> all()

  Notification getGlobal()

  Notification get(HierarchicalLevel level, String name)

  void saveGlobal(Notification notification)

  void save(HierarchicalLevel level, String name, Notification notification)

  void delete(HierarchicalLevel level, String name)
}

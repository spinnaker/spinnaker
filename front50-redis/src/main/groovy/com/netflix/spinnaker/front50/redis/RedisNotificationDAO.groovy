/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.redis

import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel
import com.netflix.spinnaker.front50.model.notification.Notification
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions

class RedisNotificationDAO implements NotificationDAO {

  static final String BOOKING_KEEPING_KEY = 'com.netflix.spinnaker:front50:notifications'

  RedisTemplate<String, Notification> redisTemplate

  @Override
  Collection<Notification> all() {
    return redisTemplate.opsForHash()
      .scan(BOOKING_KEEPING_KEY, ScanOptions.scanOptions().match('*').build())
      .withCloseable { Cursor<Map> c ->
      c.collect{ it.value }
    }
  }

  @Override
  Notification getGlobal() {
    get(HierarchicalLevel.GLOBAL, Notification.GLOBAL_ID)
  }

  @Override
  Notification get(HierarchicalLevel level, String name) {
    redisTemplate.opsForHash().get(BOOKING_KEEPING_KEY, name) ?: [email: []] as Notification // an empty Notification is expected for applications that do not exist
  }

  @Override
  void saveGlobal(Notification notification) {
    save(HierarchicalLevel.GLOBAL, Notification.GLOBAL_ID, notification)
  }

  @Override
  void save(HierarchicalLevel level, String name, Notification notification) {
    notification.level = level
    notification.name = name

    redisTemplate.opsForHash().put(BOOKING_KEEPING_KEY, name, notification)
  }

  @Override
  void delete(HierarchicalLevel level, String name) {
    redisTemplate.opsForHash().delete(BOOKING_KEEPING_KEY, name)
  }


}

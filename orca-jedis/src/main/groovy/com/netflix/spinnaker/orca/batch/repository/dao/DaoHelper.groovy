/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch.repository.dao

import groovy.transform.CompileStatic
import org.springframework.batch.core.Entity
import org.springframework.dao.OptimisticLockingFailureException
import redis.clients.jedis.JedisCommands

@CompileStatic
class DaoHelper {
  static void checkOptimisticLock(JedisCommands jedis, String key, Entity entity) {
    def persistedVersion = jedis.hget(key, "version").toInteger()
    if (entity.version != persistedVersion) {
      def typeName = entity.getClass().simpleName.replaceAll(/(?<=[a-z])([A-Z])/, / $1/).toLowerCase()
      throw new OptimisticLockingFailureException("Attempt to update $typeName id=$entity.id with wrong version ($entity.version), where current version is $persistedVersion")
    }
  }
}

/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.gate.security.rolesprovider

import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.context.SecurityContext
import org.springframework.session.ExpiringSession
import org.springframework.session.Session
import org.springframework.session.SessionRepository

@Slf4j
@Configuration
@ConditionalOnExpression('!${services.fiat.enabled:false}')
class UserRolesSyncer {
  @Autowired
  RedisTemplate sessionRedisTemplate

  @Autowired
  SessionRepository<? extends ExpiringSession> repository

  @Autowired
  UserRolesProvider userRolesProvider

  /**
   * Check all sessions to see whether the session user's roles have changed. If so, delete the session.
   * Repeat every 10 minutes, after an initial delay.
   */
  @Scheduled(initialDelay = 10000L, fixedRate = 600000L)
  public void sync() {
    Map<String, String> usernameSessionIdMap = [:]
    Map<String, Collection<String>> usernameCurrentGroupsMap = [:]
    Set<String> sessionKeys = sessionRedisTemplate.execute(new RedisCallback<Set<String>>() {
      @Override
      public Set<String> doInRedis(RedisConnection connection) throws DataAccessException {
        def results = new HashSet<String>()
        def options = ScanOptions.scanOptions().match('*session:sessions*').count(1000).build()
        connection.scan(options).withCloseable { Cursor<byte[]> sessions ->
          for (byte[] sessionRaw : sessions) {
            String session = sessionRedisTemplate.getStringSerializer().deserialize(sessionRaw)
            results.add(session)
          }
        }
        return results
      }
    })

    Set<String> sessionIds = sessionKeys.collect { String key ->
      def toks = key.split(":")
      toks[toks.length - 1]
    }

    sessionIds.each { String id ->
      Session session = repository.getSession(id)
      if (session) { // getSession(id) may return null if session is expired but not reaped
        def secCtx = session.getAttribute("SPRING_SECURITY_CONTEXT") as SecurityContext
        def principal = secCtx?.authentication?.principal
        if (principal && principal instanceof User) {
          usernameSessionIdMap[principal.username] = id
          usernameCurrentGroupsMap[principal.username] = principal.roles
        }
      }
    }

    def newGroupsMap = userRolesProvider.multiLoadRoles(usernameSessionIdMap.keySet())
    def sessionIdsToDelete = []
    newGroupsMap.each { String username, Collection<String> newGroups ->
      // cast for equals check to work
      List<String> newList = newGroups as List
      List<String> oldList = usernameCurrentGroupsMap[username] as List
      if (oldList != newList) {
        sessionIdsToDelete.add(usernameSessionIdMap[username])
        log.warn("Removing session for ${username} (oldList: ${oldList}, newList: ${newList})")
      }
    }

    def keysToDelete = sessionIdsToDelete.collect { String id -> "spring:session:sessions:" + id }
    sessionRedisTemplate.delete(keysToDelete)

    if (keysToDelete) {
      log.info("Invalidated {} user sessions due to changed group memberships.", keysToDelete.size())
    }
  }
}

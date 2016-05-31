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

import com.netflix.spinnaker.gate.redis.EmbeddedRedis
import com.netflix.spinnaker.security.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.OAuth2Request
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.session.data.redis.RedisOperationsSessionRepository
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

@WebAppConfiguration
@ContextConfiguration
class UserRolesSyncerSpec extends Specification {

  /**
   * Port the EmbeddedRedis instance associated with this test is listening on.
   */
  static Integer port

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Autowired
  RedisTemplate sessionRedisTemplate

  @Autowired
  RedisOperationsSessionRepository repository

  @Shared
  @Subject
  UserRolesSyncer userRolesSyncer

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    embeddedRedis.jedis.flushDB()
    port = embeddedRedis.port
  }

  def cleanup() {
    embeddedRedis.jedis.flushDB()
  }

  def createSpringSession(String email, List<String> roles) {
    def session = repository.createSession()

    User spinnakerUser = new User(
        email: email,
        firstName: "User",
        lastName: "McUserFace",
        allowedAccounts: ["AnonAllowedAccount"],
        roles: roles,
    )

    PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
        spinnakerUser,
        null /* credentials */,
        spinnakerUser.authorities
    )

    // impl copied from userInfoTokenServices
    OAuth2Request storedRequest = new OAuth2Request(null, "fake_test_client_id", null, true /*approved*/,
        null, null, null, null, null);

    def oauthentication = new OAuth2Authentication(storedRequest, authentication)
    def secCtx = new SecurityContextImpl()
    secCtx.setAuthentication(oauthentication)
    session.setAttribute("SPRING_SECURITY_CONTEXT", secCtx)

    repository.save(session)
    return session.getId()
  }

  def "should invalidate the session"() {
    given: "the session's groups are not up to date"
    userRolesSyncer = new UserRolesSyncer(sessionRedisTemplate: sessionRedisTemplate, repository: repository)
    def sessionId = createSpringSession("user@mcuserface.com", ["noob"])
    def rolesProvider = new UserRolesProvider() {
      @Override
      Collection<String> loadRoles(String userEmail) {
        return []
      }

      @Override
      Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
        return ["user@mcuserface.com": ["real_user"]]
      }
    }
    userRolesSyncer.userRolesProvider = rolesProvider

    when: "we sync the groups"
    userRolesSyncer.syncUserGroups()

    then: "no sessions should be present"
    repository.getSession(sessionId) == null
  }

  def "should not invalidate the session"() {
    given: "the session's groups are up to date"
    userRolesSyncer = new UserRolesSyncer(sessionRedisTemplate: sessionRedisTemplate, repository: repository)
    def sessionId = createSpringSession("user1@mcuserface.com", ["noob"])
    def rolesProvider = new UserRolesProvider() {
      @Override
      Collection<String> loadRoles(String userEmail) {
        return []
      }

      @Override
      Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
        return ["user1@mcuserface.com": ["noob"]]
      }
    }
    userRolesSyncer.userRolesProvider = rolesProvider

    when: "we sync the groups"
    userRolesSyncer.syncUserGroups()

    then: "our session should be present"
    repository.getSession(sessionId) != null
  }

  def "should invalidate only bad sessions"() {
    given: "the session's groups are up to date"
    userRolesSyncer = new UserRolesSyncer(sessionRedisTemplate: sessionRedisTemplate, repository: repository)
    def goodSessionId = createSpringSession("user2@mcuserface.com", ["noob", "real_user"])
    def badSessionId = createSpringSession("admin@mcuserface.com", ["real_user"])
    def rolesProvider = new UserRolesProvider() {
      @Override
      Collection<String> loadRoles(String userEmail) {
        return []
      }

      @Override
      Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
        return [
            "user2@mcuserface.com": ["noob", "real_user"],
            "admin@mcuserface.com": [],
        ]
      }
    }
    userRolesSyncer.userRolesProvider = rolesProvider

    when: "we sync the groups"
    userRolesSyncer.syncUserGroups()

    then: "our session should be present"
    repository.getSession(goodSessionId) != null
    repository.getSession(badSessionId) == null
  }

  @Configuration
  @EnableRedisHttpSession
  static class Config {
    @Bean
    public JedisConnectionFactory connectionFactory() {
      URI redis = URI.create("redis://localhost:" + port.toString())
      def factory = new JedisConnectionFactory()
      factory.hostName = redis.host
      factory.port = redis.port
      if (redis.userInfo) {
        factory.password = redis.userInfo.split(":", 2)[1]
      }
      factory
    }
  }
}

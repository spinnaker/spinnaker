/*
 * Copyright 2015 Netflix, Inc.
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

package org.springframework.session.data.redis.config.annotation.web.http

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotationMetadata
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.session.ExpiringSession
import org.springframework.session.data.redis.RedisOperationsSessionRepository

@Configuration
class GateRedisHttpSessionConfiguration extends RedisHttpSessionConfiguration {

  @Value('${session.expiration:1800}')
  int expiration

  public void setImportMetadata(AnnotationMetadata importMetadata) {
  }

  @Bean
  public RedisOperationsSessionRepository sessionRepository(RedisTemplate<String, ExpiringSession> sessionRedisTemplate) {
    RedisOperationsSessionRepository sessionRepository = new RedisOperationsSessionRepository(sessionRedisTemplate);
    sessionRepository.setDefaultMaxInactiveInterval(expiration);
    return sessionRepository;
  }

  @Override
  public RedisHttpSessionConfiguration.EnableRedisKeyspaceNotificationsInitializer enableRedisKeyspaceNotificationsInitializer(RedisConnectionFactory connectionFactory) {
    null
  }
}

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

import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.notification.Notification
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate
import com.netflix.spinnaker.front50.model.project.Project
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableConfigurationProperties(RedisConfigurationProperties)
@ConditionalOnExpression('${spinnaker.redis.enabled:false}')
class RedisConfig {
  @Bean
  RedisApplicationDAO redisApplicationDAO(RedisTemplate<String, Application> template) {
    new RedisApplicationDAO(redisTemplate: template)
  }

  @Bean
  RedisProjectDAO redisProjectDAO(RedisTemplate<String, Project> template) {
    new RedisProjectDAO(redisTemplate: template)
  }

  @Bean
  RedisPipelineStrategyDAO redisPipelineStrategyDAO(RedisTemplate<String, Pipeline> template) {
    new RedisPipelineStrategyDAO(redisTemplate: template)
  }

  @Bean
  RedisPipelineDAO redisPipelineDAO(RedisTemplate<String, Pipeline> template) {
    new RedisPipelineDAO(redisTemplate: template)
  }

  @Bean
  RedisPipelineTemplateDAO redisPipelineTemplateDAO(RedisTemplate<String, PipelineTemplate> template) {
    new RedisPipelineTemplateDAO(redisTemplate: template)
  }

  @Bean
  RedisNotificationDAO redisNotificationDAO(RedisTemplate<String, Notification> template) {
    new RedisNotificationDAO(redisTemplate: template)
  }

  @Bean
  RedisConnectionFactory jedisConnectionFactory(RedisConfigurationProperties redisConfigurationProperties) {
    JedisConnectionFactory factory = new JedisConnectionFactory()
    factory.setHostName(redisConfigurationProperties.host)
    factory.setPort(redisConfigurationProperties.port)

    factory
  }

  @Bean
  RedisTemplate<String, Application> applicationRedisTemplate(RedisConnectionFactory connectionFactory,
                                                              StringRedisSerializer stringRedisSerializer) {

    RedisTemplate<String, Application> template = new RedisTemplate<>()
    template.connectionFactory = connectionFactory
    template.keySerializer = stringRedisSerializer
    template.hashKeySerializer = stringRedisSerializer
    template.hashValueSerializer = new Jackson2JsonRedisSerializer<>(Application)

    template
  }

  @Bean
  RedisTemplate<String, Project> projectRedisTemplate(RedisConnectionFactory connectionFactory,
                                                      StringRedisSerializer stringRedisSerializer) {

    RedisTemplate<String, Project> template = new RedisTemplate<>()
    template.connectionFactory = connectionFactory
    template.keySerializer = stringRedisSerializer
    template.hashKeySerializer = stringRedisSerializer
    template.hashValueSerializer = new Jackson2JsonRedisSerializer<>(Project)

    template
  }

  @Bean
  RedisTemplate<String, Pipeline> pipelineRedisTemplate(RedisConnectionFactory connectionFactory,
                                                        StringRedisSerializer stringRedisSerializer) {

    RedisTemplate<String, Pipeline> template = new RedisTemplate<>()
    template.connectionFactory = connectionFactory
    template.keySerializer = stringRedisSerializer
    template.hashKeySerializer = stringRedisSerializer
    template.hashValueSerializer = new Jackson2JsonRedisSerializer<>(Pipeline)

    template
  }

  @Bean
  RedisTemplate<String, PipelineTemplate> pipelineTemplateRedisTemplate(RedisConnectionFactory connectionFactory,
                                                                        StringRedisSerializer stringRedisSerializer) {

    RedisTemplate<String, PipelineTemplate> template = new RedisTemplate<>()
    template.connectionFactory = connectionFactory
    template.keySerializer = stringRedisSerializer
    template.hashKeySerializer = stringRedisSerializer
    template.hashValueSerializer = new Jackson2JsonRedisSerializer<>(PipelineTemplate)

    template
  }

  @Bean
  RedisTemplate<String, Notification> notificationRedisTemplate(RedisConnectionFactory connectionFactory,
                                                                StringRedisSerializer stringRedisSerializer) {

    RedisTemplate<String, Notification> template = new RedisTemplate<>()
    template.connectionFactory = connectionFactory
    template.keySerializer = stringRedisSerializer
    template.hashKeySerializer = stringRedisSerializer
    template.hashValueSerializer = new Jackson2JsonRedisSerializer<>(Notification)

    template
  }

  @Bean
  StringRedisSerializer stringRedisSerializer() {
    new StringRedisSerializer()
  }

}

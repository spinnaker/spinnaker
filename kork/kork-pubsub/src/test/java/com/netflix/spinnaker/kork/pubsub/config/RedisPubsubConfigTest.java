/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.pubsub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.pubsub.PubsubPublishers;
import com.netflix.spinnaker.kork.pubsub.PubsubSubscribers;
import com.netflix.spinnaker.kork.pubsub.redis.RedisPubsub;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisPubsubConfigTest {
  @Test
  void createsRedisPubsubWhenStringRedisTemplateIsProvided() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(TestRedisTemplateConfig.class, RedisPubsubConfig.class);
      context.refresh();

      assertThat(context.containsBean("redisPubsub")).isTrue();
      assertThat(context.getBean(RedisPubsub.class)).isNotNull();
      assertThat(context.getBean(PubsubPublishers.class)).isNotNull();
      assertThat(context.getBean(PubsubSubscribers.class)).isNotNull();
    }
  }

  @Test
  void pubsubConfigDoesNotAutoConfigureRedisConnectionFactory() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(PubsubConfig.class);
      context.refresh();

      assertThat(context.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
      assertThat(context.getBeansOfType(StringRedisTemplate.class)).isEmpty();
    }
  }

  @Configuration
  static class TestRedisTemplateConfig {
    @Bean
    StringRedisTemplate stringRedisTemplate() {
      LettuceConnectionFactory factory = new LettuceConnectionFactory();
      factory.afterPropertiesSet();
      StringRedisTemplate template = new StringRedisTemplate(factory);
      template.afterPropertiesSet();
      return template;
    }
  }
}

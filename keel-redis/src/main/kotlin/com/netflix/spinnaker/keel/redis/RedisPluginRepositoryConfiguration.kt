package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.registry.PluginRepository
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedisPluginRepositoryConfiguration {
  @Bean
  fun pluginRepository(redisClientSelector: RedisClientSelector): PluginRepository =
    RedisPluginRepository(redisClientSelector.primary("default"))
}

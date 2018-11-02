/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.redis.RedisAssetRepository
import com.netflix.spinnaker.keel.redis.RedisPluginRepository
import com.netflix.spinnaker.keel.registry.PluginRepository
import com.netflix.spinnaker.kork.dynomite.DynomiteClientConfiguration
import com.netflix.spinnaker.kork.jedis.JedisClientConfiguration
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock

@Configuration
@Import(JedisClientConfiguration::class, DynomiteClientConfiguration::class)
class RedisConfiguration {
  @Bean
  fun pluginRepository(redisClientSelector: RedisClientSelector): PluginRepository =
    RedisPluginRepository(redisClientSelector.primary("default"))

  @Bean
  fun assetRepository(redisClientSelector: RedisClientSelector, objectMapper: ObjectMapper, clock: Clock): AssetRepository =
    RedisAssetRepository(redisClientSelector.primary("default"), objectMapper, clock)
}

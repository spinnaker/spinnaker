/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool
/**
 * Establish a connection to the Jedis instance
 */
@Configuration
@ConditionalOnExpression('${redis.enabled:true}')
class JedisConfig {

    @Bean
    JedisPool jedis(IgorConfigurationProperties igorConfigurationProperties) {
        new JedisPool(new URI(igorConfigurationProperties.redis.connection), igorConfigurationProperties.redis.timeout)
    }

    @Bean
    RedisClientDelegate redisClientDelegate(JedisPool jedisPool) {
        return new JedisClientDelegate(jedisPool)
    }

}

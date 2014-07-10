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

package com.netflix.spinnaker.kato.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol
import redis.embedded.RedisServer

import javax.annotation.PreDestroy

/**
 * Configuration for embedded redis instance and associated jedis
 **/
@Configuration
@CompileStatic
@Slf4j
@ConditionalOnMissingClass(name = 'com.netflix.dyno.jedis.DynoJedisClient')
class JedisConfig {

  @Autowired
  Environment environment

  RedisServer redisServer

  @Bean
  @SuppressWarnings('GStringExpressionWithinString')
  JedisCommands jedis(@Value('${redis.port:0}') Integer port,
                      @Value('${redis.host:127.0.0.1}') String host,
                      @Value('${redis.connection:none}') String connection
  ) {
    if (connection != 'none') {
      URI redisURI = new URI(connection)
      return new JedisPool(new JedisPoolConfig(),
        redisURI.host,
        redisURI.port,
        Protocol.DEFAULT_TIMEOUT,
        redisURI.userInfo.split(':', 2)[1]).resource
    }

    int redisPort = port
    if (host == '127.0.0.1') {
      if (redisPort == 0) {
        ServerSocket serverSocket = new ServerSocket(0)
        redisPort = serverSocket.localPort
        serverSocket.close()
      }
      log.info "starting embedded redis server on ${host}:${port}"
      redisServer = new RedisServer(redisPort)
      redisServer.start()
      log.info "started embedded redis server on ${host}:${port}"
    }
    new Jedis(host, redisPort)
  }

  @PreDestroy
  void destroy() {
    redisServer?.stop()
  }

}
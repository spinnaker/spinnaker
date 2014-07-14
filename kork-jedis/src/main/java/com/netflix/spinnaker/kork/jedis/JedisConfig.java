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

package com.netflix.spinnaker.kork.jedis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.embedded.RedisServer;

import javax.annotation.PreDestroy;
import java.net.ServerSocket;
import java.net.URI;

/**
 * Configuration for embedded redis instance and associated jedis
 */
@ConditionalOnMissingClass(name = "com.netflix.dyno.jedis.DynoJedisClient")
public class JedisConfig {

  @Autowired
  Environment environment;

  RedisServer redisServer;

  private static final Logger log = LoggerFactory.getLogger(JedisConfig.class);

  @Bean
  public JedisCommands jedis(@Value("${redis.port:0}") int port,
                             @Value("${redis.host:127.0.0.1}") String host,
                             @Value("${redis.connection:none}") String connection
  ) {

    if (!connection.equals("none")) {
      try {
        URI redisURI = new URI(connection);
        return new JedisPool(new JedisPoolConfig(),
          redisURI.getHost(),
          redisURI.getPort(),
          Protocol.DEFAULT_TIMEOUT,
          redisURI.getUserInfo().split(":", 2)[1]).getResource();
      } catch (Exception e) {
        log.error("Could not connect to server", e);
        return null;
      }
    }

    int redisPort = port;
    if (host.equals("127.0.0.1")) {
      try {
        if (redisPort == 0) {
          ServerSocket serverSocket = new ServerSocket(0);
          redisPort = serverSocket.getLocalPort();
          serverSocket.close();
        }
        log.info("starting embedded redis server on" + host + ":" + redisPort);
        redisServer = new RedisServer(redisPort);
        redisServer.start();
        log.info("started embedded redis server");
      } catch (Exception e) {
        log.error("Could not start embedded redis", e);
      }
    }
    return new Jedis(host, redisPort);
  }

  @PreDestroy
  void destroy() {
    if (redisServer != null) {
      log.info("stopping redis server");
      try {
        redisServer.stop();
      } catch (Exception e) {
        log.error("could not stop redis server", e);
      }
    }
  }

}

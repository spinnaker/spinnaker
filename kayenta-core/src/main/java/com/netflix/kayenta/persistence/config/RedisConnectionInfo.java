/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.persistence.config;

import lombok.Builder;
import lombok.Data;
import redis.clients.jedis.Protocol;
import redis.clients.util.JedisURIHelper;

import java.net.URI;

@Builder
@Data
public class RedisConnectionInfo {

  private static final String REDIS_SSL_SCHEME = "rediss://";
  private String host;
  private int port;
  private int database;
  private String password;
  private boolean ssl;

  static RedisConnectionInfo parseConnectionUri(String connection) {
    URI redisConnection = URI.create(connection);
    String host = redisConnection.getHost();
    int port = redisConnection.getPort() == -1 ? Protocol.DEFAULT_PORT : redisConnection.getPort();
    int database = JedisURIHelper.getDBIndex(redisConnection);
    String password = JedisURIHelper.getPassword(redisConnection);
    boolean ssl = connection.startsWith(REDIS_SSL_SCHEME);

    return RedisConnectionInfo.builder().host(host).port(port).database(database).password(password).ssl(ssl).build();
  }
}

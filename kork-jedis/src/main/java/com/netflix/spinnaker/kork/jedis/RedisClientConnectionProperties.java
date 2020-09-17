/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.kork.jedis;

import java.net.URI;
import redis.clients.jedis.Protocol;

public class RedisClientConnectionProperties {
  private URI connection;

  public RedisClientConnectionProperties(URI connection) {
    this.connection = connection;
  }

  public boolean isSSL() {
    return this.connection.getScheme().equals("rediss");
  }

  public String password() {
    if (this.connection.getUserInfo() == null || !this.connection.getUserInfo().contains(":")) {
      return null;
    }
    return this.connection.getUserInfo().split(":", 2)[1];
  }

  public String addr() {
    return this.connection.getHost();
  }

  public int port() {
    return this.connection.getPort() == -1 ? Protocol.DEFAULT_PORT : this.connection.getPort();
  }

  public int database() {
    if (connection.getPath() == null
        || connection.getPath().equals("")
        || connection.getPath().equals("/")) {
      return Protocol.DEFAULT_DATABASE;
    }
    return Integer.parseInt(connection.getPath().substring(1));
  }
}

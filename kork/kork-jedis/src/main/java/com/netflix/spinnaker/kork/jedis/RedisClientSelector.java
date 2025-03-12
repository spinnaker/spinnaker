/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.kork.jedis.exception.RedisClientNotFound;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisClientSelector {

  private static final String DEFAULT = "default";
  private static final String PRIMARY = "primary";
  private static final String PREVIOUS = "previous";

  private final Logger log = LoggerFactory.getLogger(RedisClientSelector.class);

  private final List<RedisClientDelegate> clients;

  public RedisClientSelector(List<RedisClientDelegate> clients) {
    this.clients = clients;
    clients.forEach(
        client -> {
          log.info("Configured {} using {}", client.name(), client.getClass().getSimpleName());
        });
  }

  public RedisClientDelegate primary(String name) {
    return primary(name, true);
  }

  public Optional<RedisClientDelegate> previous(String name) {
    return previous(name, true);
  }

  public RedisClientDelegate primary(String name, boolean fallbackToDefault) {
    return select(name, true, fallbackToDefault)
        .orElseThrow(
            () ->
                new RedisClientNotFound(
                    "Could not find primary Redis client by name '"
                        + name
                        + "' and no default configured"));
  }

  public Optional<RedisClientDelegate> previous(String name, boolean fallbackToDefault) {
    return select(name, false, fallbackToDefault);
  }

  private Optional<RedisClientDelegate> select(
      String name, boolean primary, boolean fallbackToDefault) {
    String stdName = getName(primary, name);

    Optional<RedisClientDelegate> client =
        clients.stream().filter(it -> stdName.equals(it.name())).findFirst();

    if (!client.isPresent() && fallbackToDefault) {
      String defaultName = getName(primary, DEFAULT);
      client = clients.stream().filter(it -> defaultName.equals(it.name())).findFirst();
    }

    return client;
  }

  public static String getName(boolean primary, String name) {
    return (primary ? PRIMARY : PREVIOUS) + name.substring(0, 1).toUpperCase() + name.substring(1);
  }
}

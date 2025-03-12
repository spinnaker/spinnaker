/*
 * Copyright 2024 Wise PLC
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

package com.netflix.spinnaker.gate.services;

import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Component
public class SessionService {

  private final JedisPool jedisPool;

  public SessionService(@Autowired JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  public void deleteSpringSessions() {
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> keys = jedis.keys("spring:session*");

      if (!keys.isEmpty()) {
        jedis.del(keys.toArray(new String[0]));
      }
    }
  }
}

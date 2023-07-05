/*
 * Copyright 2023 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.kork.jedis.telemetry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;

public class InstrumentedJedisPoolTest {

  private Registry registry = mock(Registry.class);

  private JedisPool jedisPool = new JedisPool();

  private InstrumentedJedisPool instrumentedJedisPool =
      new InstrumentedJedisPool(registry, jedisPool);

  @Test
  public void getInternalPoolReference() {
    assertNotNull(instrumentedJedisPool.getInternalPoolReference());
  }
}

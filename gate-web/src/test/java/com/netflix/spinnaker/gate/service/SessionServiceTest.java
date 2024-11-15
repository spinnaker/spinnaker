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

package com.netflix.spinnaker.gate.service;

import com.netflix.spinnaker.gate.services.SessionService;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

public class SessionServiceTest {

  private static EmbeddedRedis embeddedRedis;

  @BeforeAll
  public static void setupSpec() {
    embeddedRedis = EmbeddedRedis.embed();
  }

  @AfterAll
  public static void tearDown() {
    if (embeddedRedis != null) {
      embeddedRedis.destroy();
    }
  }

  @Test
  public void shouldDeleteSpringSessions() {
    // Given
    Jedis jedis = embeddedRedis.getJedis();
    jedis.set("spring:session:session1", "session1-data");
    jedis.set("spring:session:session2", "session2-data");
    jedis.set("other:key", "other-data");

    SessionService subject = new SessionService(embeddedRedis.getPool());

    // When
    subject.deleteSpringSessions();

    // Then
    Set<String> springSessionKeys = jedis.keys("spring:session*");
    Set<String> otherKeys = jedis.keys("other:key");

    Assertions.assertTrue(
        springSessionKeys.isEmpty(), "Spring session keys should have been deleted");
    Assertions.assertEquals(1, otherKeys.size(), "Other keys should remain");
  }
}

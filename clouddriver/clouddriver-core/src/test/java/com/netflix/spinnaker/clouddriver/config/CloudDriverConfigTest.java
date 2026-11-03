/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class CloudDriverConfigTest {

  private static final long EPOCH_MILLIS = 1790816562123L;

  /**
   * The AWS caching agents enable WRITE_DATES_AS_TIMESTAMPS on the shared mapper, so numeric
   * timestamps reach the cache and Deck. Every consumer reads them as epoch millis -- see {@code
   * AmazonInstance.getLaunchTime()} comparing against {@code System.currentTimeMillis()}.
   */
  private ObjectMapper sharedObjectMapper() {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    new CloudDriverConfig().defaultObjectMapperCustomizer(new ArrayList<>()).customize(builder);
    return builder.build().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Test
  void writesInstantAsEpochMillis() {
    Map<String, Object> serialized =
        sharedObjectMapper()
            .convertValue(Map.of("timestamp", Instant.ofEpochMilli(EPOCH_MILLIS)), Map.class);

    assertThat(serialized.get("timestamp")).isEqualTo(EPOCH_MILLIS);
  }

  @Test
  void readsEpochMillisBackAsTheSameInstant() throws Exception {
    ObjectMapper mapper = sharedObjectMapper();
    String json = "{\"timestamp\":" + EPOCH_MILLIS + "}";

    TimestampHolder holder = mapper.readValue(json, TimestampHolder.class);

    assertThat(holder.timestamp).isEqualTo(Instant.ofEpochMilli(EPOCH_MILLIS));
  }

  static class TimestampHolder {
    public Instant timestamp;
  }
}

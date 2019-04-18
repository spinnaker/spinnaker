/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.kayenta.atlas.backends;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.atlas.enabled")
public class AtlasStorageUpdaterService extends AbstractHealthIndicator {
  private final RetrofitClientFactory retrofitClientFactory;
  private final ObjectMapper objectMapper;
  private final OkHttpClient okHttpClient;
  private final List<AtlasStorageUpdater> atlasStorageUpdaters = new ArrayList<>();
  private int checksCompleted = 0;

  @Autowired
  public AtlasStorageUpdaterService(RetrofitClientFactory retrofitClientFactory, ObjectMapper objectMapper, OkHttpClient okHttpClient) {
    this.retrofitClientFactory = retrofitClientFactory;
    this.objectMapper = objectMapper;
    this.okHttpClient = okHttpClient;
  }

  @Scheduled(initialDelay = 2000, fixedDelay=122000)
  public void run() {
    // TODO: this will fetch the same uri even if they share the same URI.
    // TODO: It also has locking issues, in that we could hold a lock for a long time.
    // TODO: Locking may not matter as we should rarely, if ever, modify this list.
    // TODO: Although, for healthcheck, it may...
    int checks = 0;
    for (AtlasStorageUpdater updater: atlasStorageUpdaters) {
      synchronized(this) {
        boolean result = updater.run(retrofitClientFactory, objectMapper, okHttpClient);
        if (result)
          checks++;
      }
    }
    checksCompleted = checks;
  }

  public synchronized void add(AtlasStorageUpdater updater) {
    atlasStorageUpdaters.add(updater);
  }

  @Override
  protected synchronized void doHealthCheck(Health.Builder builder) throws Exception {
    if (checksCompleted == atlasStorageUpdaters.size()) {
      builder.up();
    } else {
      builder.down();
    }
    builder.withDetail("checksCompleted", checksCompleted);
    builder.withDetail("checksExpected", atlasStorageUpdaters.size());
  }
}

/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.agent;

import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.redis.JedisSource;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CleanupPendingOnDemandCachesAgent implements RunnableAgent, CustomScheduledAgent {
  private static final Logger log = LoggerFactory.getLogger(CleanupPendingOnDemandCachesAgent.class);

  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30);
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

  private final JedisSource jedisSource;
  private final ApplicationContext applicationContext;
  private final long pollIntervalMillis;
  private final long timeoutMillis;

  public CleanupPendingOnDemandCachesAgent(JedisSource jedisSource,
                                           ApplicationContext applicationContext) {
    this(jedisSource, applicationContext, DEFAULT_POLL_INTERVAL_MILLIS, DEFAULT_TIMEOUT_MILLIS);
  }

  private CleanupPendingOnDemandCachesAgent(JedisSource jedisSource,
                                            ApplicationContext applicationContext,
                                            long pollIntervalMillis,
                                            long timeoutMillis) {
    this.jedisSource = jedisSource;
    this.applicationContext = applicationContext;
    this.pollIntervalMillis = pollIntervalMillis;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public String getAgentType() {
    return CleanupPendingOnDemandCachesAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return CoreProvider.PROVIDER_NAME;
  }

  @Override
  public void run() {
    run(getCatsModule().getProviderRegistry().getProviders());
  }

  void run(Collection<Provider> providers) {
   providers.forEach(provider -> {
      String onDemandSetName = provider.getProviderName() + ":onDemand:members";
      List<String> onDemandKeys = scanMembers(onDemandSetName).stream()
        .filter(s -> !s.equals("_ALL_"))
        .collect(Collectors.toList());

      try (Jedis jedis = jedisSource.getJedis()) {
        Pipeline pipeline = jedis.pipelined();
        onDemandKeys.forEach(k -> pipeline.get(provider.getProviderName() + ":onDemand:attributes:" + k));

        List existingOnDemandKeys = pipeline.syncAndReturnAll();

        Set<String> onDemandKeysToRemove = new HashSet<>();
        for (int i = 0; i < onDemandKeys.size(); i++) {
          if (existingOnDemandKeys.get(i) == null) {
            onDemandKeysToRemove.add(onDemandKeys.get(i));
          }
        }

        if (!onDemandKeysToRemove.isEmpty()) {
          log.info("Removing {} from {}", onDemandKeysToRemove.size(), onDemandSetName);
          log.debug("Removing {} from {}", onDemandKeysToRemove, onDemandSetName);

          jedis.srem(onDemandSetName, onDemandKeysToRemove.toArray(new String[onDemandKeysToRemove.size()]));
        }
      }
    });
  }

  public long getPollIntervalMillis() {
    return pollIntervalMillis;
  }

  public long getTimeoutMillis() {
    return timeoutMillis;
  }

  private Set<String> scanMembers(String setKey) {
    try (Jedis jedis = jedisSource.getJedis()) {
      final Set<String> matches = new HashSet<>();
      final ScanParams scanParams = new ScanParams().count(25000);
      String cursor = "0";
      while (true) {
        final ScanResult<String> scanResult = jedis.sscan(setKey, cursor, scanParams);
        matches.addAll(scanResult.getResult());
        cursor = scanResult.getStringCursor();
        if ("0".equals(cursor)) {
          return matches;
        }
      }
    }
  }

  private CatsModule getCatsModule() {
    return applicationContext.getBean(CatsModule.class);
  }
}

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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import redis.clients.jedis.Response;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

public class CleanupPendingOnDemandCachesAgent implements RunnableAgent, CustomScheduledAgent {
  private static final Logger log =
      LoggerFactory.getLogger(CleanupPendingOnDemandCachesAgent.class);

  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30);
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

  private final RedisCacheOptions redisCacheOptions;
  private final RedisClientDelegate redisClientDelegate;
  private final ApplicationContext applicationContext;
  private final long pollIntervalMillis;
  private final long timeoutMillis;

  public CleanupPendingOnDemandCachesAgent(
      RedisCacheOptions redisCacheOptions,
      RedisClientDelegate redisClientDelegate,
      ApplicationContext applicationContext) {
    this(
        redisCacheOptions,
        redisClientDelegate,
        applicationContext,
        DEFAULT_POLL_INTERVAL_MILLIS,
        DEFAULT_TIMEOUT_MILLIS);
  }

  private CleanupPendingOnDemandCachesAgent(
      RedisCacheOptions redisCacheOptions,
      RedisClientDelegate redisClientDelegate,
      ApplicationContext applicationContext,
      long pollIntervalMillis,
      long timeoutMillis) {
    this.redisCacheOptions = redisCacheOptions;
    this.redisClientDelegate = redisClientDelegate;
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
    providers.forEach(
        provider -> {
          String onDemandSetName = provider.getProviderName() + ":onDemand:members";
          List<String> onDemandKeys =
              scanMembers(onDemandSetName).stream()
                  .filter(s -> !s.equals("_ALL_"))
                  .collect(Collectors.toList());

          Map<String, Response<Boolean>> existingOnDemandKeys = new HashMap<>();
          if (redisClientDelegate.supportsMultiKeyPipelines()) {
            redisClientDelegate.withMultiKeyPipeline(
                pipeline -> {
                  for (List<String> partition :
                      Iterables.partition(onDemandKeys, redisCacheOptions.getMaxDelSize())) {
                    for (String id : partition) {
                      existingOnDemandKeys.put(
                          id,
                          pipeline.exists(
                              provider.getProviderName() + ":onDemand:attributes:" + id));
                    }
                  }
                  pipeline.sync();
                });
          } else {
            redisClientDelegate.withCommandsClient(
                client -> {
                  onDemandKeys.stream()
                      .filter(
                          k ->
                              client.exists(
                                  provider.getProviderName() + "onDemand:attributes:" + k))
                      .forEach(k -> existingOnDemandKeys.put(k, new StaticResponse(Boolean.TRUE)));
                });
          }

          List<String> onDemandKeysToRemove = new ArrayList<>();
          for (String onDemandKey : onDemandKeys) {
            if (!existingOnDemandKeys.containsKey(onDemandKey)
                || !existingOnDemandKeys.get(onDemandKey).get()) {
              onDemandKeysToRemove.add(onDemandKey);
            }
          }

          if (!onDemandKeysToRemove.isEmpty()) {
            log.info("Removing {} from {}", onDemandKeysToRemove.size(), onDemandSetName);
            log.debug("Removing {} from {}", onDemandKeysToRemove, onDemandSetName);

            redisClientDelegate.withMultiKeyPipeline(
                pipeline -> {
                  for (List<String> idPartition :
                      Lists.partition(onDemandKeysToRemove, redisCacheOptions.getMaxDelSize())) {
                    String[] ids = idPartition.toArray(new String[idPartition.size()]);
                    pipeline.srem(onDemandSetName, ids);
                  }

                  pipeline.sync();
                });
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
    return redisClientDelegate.withCommandsClient(
        client -> {
          final Set<String> matches = new HashSet<>();
          final ScanParams scanParams = new ScanParams().count(redisCacheOptions.getScanSize());
          String cursor = "0";
          while (true) {
            final ScanResult<String> scanResult = client.sscan(setKey, cursor, scanParams);
            matches.addAll(scanResult.getResult());
            cursor = scanResult.getStringCursor();
            if ("0".equals(cursor)) {
              return matches;
            }
          }
        });
  }

  private CatsModule getCatsModule() {
    return applicationContext.getBean(CatsModule.class);
  }

  private static class StaticResponse extends Response<Boolean> {
    private final Boolean value;

    StaticResponse(Boolean value) {
      super(null);
      this.value = value;
    }

    @Override
    public Boolean get() {
      return value;
    }
  }
}

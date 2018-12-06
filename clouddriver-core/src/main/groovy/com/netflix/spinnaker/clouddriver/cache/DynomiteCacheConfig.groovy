/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.discovery.DiscoveryClient
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.HostSupplier
import com.netflix.dyno.connectionpool.TokenMapSupplier
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import com.netflix.dyno.jedis.DynoJedisClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.compression.CompressionStrategy
import com.netflix.spinnaker.cats.compression.GZipCompression
import com.netflix.spinnaker.cats.compression.NoopCompression
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteCache.CacheMetrics
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteNamedCacheFactory
import com.netflix.spinnaker.cats.dynomite.cluster.DynoClusteredAgentScheduler
import com.netflix.spinnaker.cats.dynomite.cluster.DynoClusteredSortAgentScheduler
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.time.Clock
import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnExpression('${dynomite.enabled:false}')
@EnableConfigurationProperties([DynomiteConfigurationProperties, RedisConfigurationProperties, GZipCompressionStrategyProperties])
class DynomiteCacheConfig {

  @Bean
  CacheMetrics cacheMetrics(Registry registry) {
    new SpectatorDynomiteCacheMetrics(registry)
  }

  @Bean
  @ConfigurationProperties("dynomite.connectionPool")
  ConnectionPoolConfigurationImpl connectionPoolConfiguration(DynomiteConfigurationProperties dynomiteConfigurationProperties) {
    new ConnectionPoolConfigurationImpl(dynomiteConfigurationProperties.applicationName).withHashtag("{}")
  }

  @Bean
  CompressionStrategy compressionStrategy(ConnectionPoolConfigurationImpl connectionPoolConfiguration,
                                          GZipCompressionStrategyProperties properties) {
    if (!properties.enabled) {
      return new NoopCompression()
    }
    return new GZipCompression(
      properties.thresholdBytesSize,
      properties.compressEnabled && connectionPoolConfiguration.compressionStrategy != ConnectionPoolConfiguration.CompressionStrategy.THRESHOLD
    )
  }

  @Bean(destroyMethod = "stopClient")
  DynoJedisClient dynoJedisClient(DynomiteConfigurationProperties dynomiteConfigurationProperties, ConnectionPoolConfigurationImpl connectionPoolConfiguration, Optional<DiscoveryClient> discoveryClient) {
    def builder = new DynoJedisClient.Builder()
        .withApplicationName(dynomiteConfigurationProperties.applicationName)
        .withDynomiteClusterName(dynomiteConfigurationProperties.clusterName)

    discoveryClient.map({ dc ->
      builder.withDiscoveryClient(dc)
        .withCPConfig(connectionPoolConfiguration)
    }).orElseGet({
      connectionPoolConfiguration
        .withTokenSupplier(new StaticTokenMapSupplier(dynomiteConfigurationProperties.dynoHostTokens))
        .setLocalDataCenter(dynomiteConfigurationProperties.localDataCenter)
        .setLocalRack(dynomiteConfigurationProperties.localRack)

      builder
        .withHostSupplier(new StaticHostSupplier(dynomiteConfigurationProperties.dynoHosts))
        .withCPConfig(connectionPoolConfiguration)
    }).build()
  }

  @Bean
  DynomiteClientDelegate dynomiteClientDelegate(DynoJedisClient dynoJedisClient) {
    new DynomiteClientDelegate(dynoJedisClient)
  }

  @Bean
  @ConfigurationProperties("caching.redis")
  RedisCacheOptions.Builder redisCacheOptionsBuilder() {
    RedisCacheOptions.builder()
  }

  @Bean
  RedisCacheOptions redisCacheOptions(RedisCacheOptions.Builder redisCacheOptionsBuilder) {
    redisCacheOptionsBuilder.build()
  }

  @Bean
  NamedCacheFactory cacheFactory(
    @Value('${dynomite.keyspace:#{null}}') String keyspace,
    DynomiteClientDelegate dynomiteClientDelegate,
    ObjectMapper objectMapper,
    RedisCacheOptions redisCacheOptions,
    CacheMetrics cacheMetrics,
    CompressionStrategy compressionStrategy) {
    new DynomiteNamedCacheFactory(Optional.ofNullable(keyspace), dynomiteClientDelegate, objectMapper, redisCacheOptions, cacheMetrics, compressionStrategy)
  }

  @Bean
  @ConditionalOnMissingBean(NodeStatusProvider.class)
  DefaultNodeStatusProvider nodeStatusProvider() {
    new DefaultNodeStatusProvider()
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider(RedisConfigurationProperties redisConfigurationProperties) {
    new CustomSchedulableAgentIntervalProvider(
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.intervalSeconds),
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.errorIntervalSeconds),
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.timeoutSeconds)
    );
  }

  @Bean
  @ConditionalOnProperty(value = "caching.writeEnabled", matchIfMissing = true)
  AgentScheduler agentScheduler(Clock clock,
                                RedisConfigurationProperties redisConfigurationProperties,
                                RedisClientDelegate redisClientDelegate,
                                AgentIntervalProvider agentIntervalProvider,
                                NodeStatusProvider nodeStatusProvider) {
    if (redisConfigurationProperties.scheduler.equalsIgnoreCase("default")) {
      new DynoClusteredAgentScheduler(
        (DynomiteClientDelegate) redisClientDelegate,
        new DefaultNodeIdentity(),
        agentIntervalProvider,
        nodeStatusProvider
      );
    } else if (redisConfigurationProperties.scheduler.equalsIgnoreCase("sort")) {
      new DynoClusteredSortAgentScheduler(clock, redisClientDelegate, nodeStatusProvider, agentIntervalProvider, redisConfigurationProperties.parallelism ?: -1);
    } else {
      throw new IllegalStateException("redis.scheduler must be one of 'default', 'sort', or ''.");
    }
  }

  static class StaticHostSupplier implements HostSupplier {

    private final List<Host> hosts

    StaticHostSupplier(List<Host> hosts) {
      this.hosts = hosts
    }

    @Override
    List<Host> getHosts() {
      return hosts
    }
  }

  static class StaticTokenMapSupplier implements TokenMapSupplier {

    List<HostToken> hostTokens = new ArrayList<>()

    StaticTokenMapSupplier(List<HostToken> hostTokens) {
      this.hostTokens = hostTokens
    }

    @Override
    List<HostToken> getTokens(Set<Host> activeHosts) {
      return hostTokens
    }

    @Override
    HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
      return hostTokens.find { it.host == host }
    }
  }
}

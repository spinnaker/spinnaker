/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.igor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Igor's root configuration class. */
@Data
@ConfigurationProperties
public class IgorConfigurationProperties {
  @NestedConfigurationProperty private SpinnakerProperties spinnaker = new SpinnakerProperties();
  @NestedConfigurationProperty private RedisProperties redis = new RedisProperties();
  @NestedConfigurationProperty private ClientProperties client = new ClientProperties();
  @NestedConfigurationProperty private ServicesProperties services = new ServicesProperties();

  @Data
  public static class SpinnakerProperties {
    /** Redis configuration for Igor. */
    @NestedConfigurationProperty private JedisProperties jedis = new JedisProperties();

    /** Build system configuration. */
    @NestedConfigurationProperty private BuildProperties build = new BuildProperties();

    /**
     * Build system safeguard configuration.
     *
     * <p>These settings are allow various safeguards to be put into place on polling behavior.
     */
    @NestedConfigurationProperty
    private PollingSafeguardProperties pollingSafeguard = new PollingSafeguardProperties();

    /** TODO(rz): Remove, move key prefixes into kork's redis config, property migrator */
    @Data
    public static class JedisProperties {
      /** Defines a Redis key prefix. */
      private String prefix = "igor";
    }

    @Data
    public static class BuildProperties {
      /**
       * Defines whether or not the build system polling mechanism is enabled. Disabling this will
       * effectively disable any integration with a build system that depends on Igor polling it.
       */
      private boolean pollingEnabled = true;

      /**
       * The global build system poll interval.
       *
       * <p>TODO(rz): Should allow individual monitors to define their own config / logic
       */
      private int pollInterval = 60;

      /** TODO(jc): Please document */
      private int lookBackWindowMins = 10 * 60 * 60;

      /** TODO(jc): Please document */
      private boolean handleFirstBuilds = true;

      /** TODO(jc): Please document */
      private boolean processBuildsOlderThanLookBackWindow = true;
    }

    @Data
    public static class PollingSafeguardProperties {
      /**
       * Defines the upper threshold for number of new cache items before a cache update cycle will
       * be completely rejected. This is to protect against potentially re-indexing old caches due
       * to downstream service errors. Once this threshold is tripped, API-driven resolution may be
       * required. By default set higher than most use cases would require since it's a feature that
       * requires more hands-on operations.
       */
      private int itemUpperThreshold = 1000;
    }
  }

  @Data
  public static class RedisProperties {
    /**
     * The connection string to the Redis server.
     *
     * <p>Example: {@code redis://localhost:6379}
     */
    private String connection = "redis://localhost:6379";

    /**
     * The Redis socket timeout in milliseconds.
     *
     * <p>TODO(rz): Refactor to Duration.
     */
    private int timeout = 2000;

    /**
     * Docker Redis key migration configuration.
     *
     * <p>IMPORTANT: Only applicable if upgrading from from Spinnaker 1.6.0 (Igor 2.5.x).
     */
    @NestedConfigurationProperty
    private DockerV1KeyMigration dockerV1KeyMigration = new DockerV1KeyMigration();

    /**
     * TODO(rz): Surely we can delete this... it's been over 2 years since v1 migration:
     * https://github.com/spinnaker/igor/commit/a05b86b1078dfb6e01e915762e3b57672331ae88
     */
    @Data
    public static class DockerV1KeyMigration {
      /**
       * Defines how long (in days) V1 Redis keys will be available after migration for before being
       * deleted.
       *
       * <p>The duration of this should be increased if you want more time to potentially rollback.
       * This value has rapidly diminishing returns as new builds are created.
       */
      private int ttlDays = 30;

      /** The number of keys to migrate in a single group. */
      private int batchSize = 100;
    }
  }

  @Data
  public static class ClientProperties {
    /**
     * Build system client timeout in milliseconds.
     *
     * <p>TODO(rz): Refactor to Duration.
     */
    private int timeout = 30000;
  }

  @Data
  public static class ServicesProperties {
    /** Config for clouddriver connectivity. */
    @NestedConfigurationProperty
    private ServiceConfiguration clouddriver = new ServiceConfiguration();

    /** Config for echo connectivity. */
    @NestedConfigurationProperty private ServiceConfiguration echo = new ServiceConfiguration();

    /** Config for keel connectivity. */
    @NestedConfigurationProperty private ServiceConfiguration keel = new ServiceConfiguration();

    @Data
    public static class ServiceConfiguration {
      /**
       * The base url of the particular service.
       *
       * <p>Example: {@code https://localhost:8081}
       */
      private String baseUrl;
    }
  }
}

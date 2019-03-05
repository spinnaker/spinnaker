/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.igor

import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@Canonical
@ConfigurationProperties
class IgorConfigurationProperties {
    static class SpinnakerProperties {
        @Canonical
        static class JedisProperties {
            String prefix = 'igor'
        }

        @NestedConfigurationProperty
        final JedisProperties jedis = new JedisProperties()

        @Canonical
        static class BuildProperties {
            int pollInterval = 60 /* seconds */
            int lookBackWindowMins = 10
            boolean handleFirstBuilds = true
            boolean processBuildsOlderThanLookBackWindow = true
        }

        @NestedConfigurationProperty
        final BuildProperties build = new BuildProperties()

        @Canonical
        static class PollingSafeguardProperties {
            /**
             * Defines the upper threshold for number of new cache items before a cache update cycle will be completely
             * rejected. This is to protect against potentially re-indexing old caches due to downstream service errors.
             * Once this threshold is tripped, API-driven resolution may be required. By default set higher than most
             * use cases would require since it's a feature that requires more hands-on operations.
             */
            int itemUpperThreshold = 1000
        }

        @NestedConfigurationProperty
        final PollingSafeguardProperties pollingSafeguard = new PollingSafeguardProperties()
    }

    @NestedConfigurationProperty
    final SpinnakerProperties spinnaker = new SpinnakerProperties()

    @Canonical
    static class RedisProperties {
        String connection = "redis://localhost:6379"
        int timeout = 2000

        @Canonical
        static class DockerV1KeyMigration {
            int ttlDays = 30
            int batchSize = 100
        }

        @NestedConfigurationProperty
        DockerV1KeyMigration dockerV1KeyMigration = new DockerV1KeyMigration()
    }

    @NestedConfigurationProperty
    final RedisProperties redis = new RedisProperties()

    @Canonical
    static class ClientProperties {
        int timeout = 30000
    }

    @NestedConfigurationProperty
    final ClientProperties client = new ClientProperties()

    @Canonical
    static class ServicesProperties {
        @Canonical
        static class ServiceConfiguration {
            String baseUrl
        }

        @NestedConfigurationProperty
        final ServiceConfiguration clouddriver = new ServiceConfiguration()
        @NestedConfigurationProperty
        final ServiceConfiguration echo = new ServiceConfiguration()
    }

    @NestedConfigurationProperty
    final ServicesProperties services = new ServicesProperties()
}

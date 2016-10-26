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
            int pollInterval = 60
        }

        @NestedConfigurationProperty
        final BuildProperties build = new BuildProperties()
    }

    @NestedConfigurationProperty
    final SpinnakerProperties spinnaker = new SpinnakerProperties()

    @Canonical
    static class RedisProperties {
        String connection = "redis://localhost:6379"
        int timeout = 2000
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

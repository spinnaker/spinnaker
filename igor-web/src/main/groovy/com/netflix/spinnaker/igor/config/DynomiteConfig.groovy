/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.igor.config

import com.netflix.discovery.DiscoveryClient
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.HostSupplier
import com.netflix.dyno.connectionpool.TokenMapSupplier
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import com.netflix.dyno.jedis.DynoJedisClient
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("dynomite.enabled")
@EnableConfigurationProperties(DynomiteConfigurationProperties.class)
class DynomiteConfig {
    @Bean
    @ConfigurationProperties("dynomite.connection-pool")
    ConnectionPoolConfigurationImpl connectionPoolConfiguration(DynomiteConfigurationProperties dynomiteConfigurationProperties) {
        new ConnectionPoolConfigurationImpl(dynomiteConfigurationProperties.applicationName)
    }

    @Bean(destroyMethod = "stopClient")
    DynoJedisClient dynoJedisClient(DynomiteConfigurationProperties dynomiteConfigurationProperties,
                                    ConnectionPoolConfigurationImpl connectionPoolConfiguration,
                                    Optional<DiscoveryClient> discoveryClient) {
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
    RedisClientDelegate dynomiteClientDelegate(DynoJedisClient dynoJedisClient) {
        new DynomiteClientDelegate(dynoJedisClient)
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

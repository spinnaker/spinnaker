/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.astyanax;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.astyanax.AstyanaxConfiguration;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.ConnectionPoolConfiguration;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.test.EmbeddedCassandra;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;

@Configuration
@EnableConfigurationProperties(CassandraConfigurationProperties.class)
@ConditionalOnExpression("${cassandra.enabled:true}")
public class AstyanaxComponents {

    @Bean
    public ExecutorService cassandraAsyncExecutor(CassandraConfigurationProperties cassandraConfigurationProperties) {
      return Executors.newFixedThreadPool(cassandraConfigurationProperties.getAsyncExecutorPoolSize(),
        new ThreadFactoryBuilder().setDaemon(true)
          .setNameFormat("AstyanaxAsync-%d")
          .build());
    }

    @Bean
    @ConditionalOnMissingBean(AstyanaxConfiguration.class)
    @ConfigurationProperties("cassandra")
    public AstyanaxConfiguration astyanaxConfiguration(@Qualifier("cassandraAsyncExecutor") ExecutorService cassandraAsyncExecutor) {
        return new AstyanaxConfigurationImpl()
                .setDefaultReadConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM)
                .setDefaultWriteConsistencyLevel(ConsistencyLevel.CL_LOCAL_QUORUM)
                .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
                .setConnectionPoolType(ConnectionPoolType.TOKEN_AWARE)
                .setCqlVersion("3.0.0")
                .setTargetCassandraVersion("2.0")
                .setAsyncExecutor(cassandraAsyncExecutor);
    }



    @Bean
    @ConditionalOnBean(DiscoveryClient.class)
    @ConditionalOnProperty("cassandra.eureka.enabled")
    public ClusterHostSupplierFactory eurekaHostSupplier(DiscoveryClient discoveryClient) {
      return EurekaHostSupplier.factory(discoveryClient);
    }

    @Bean
    @ConditionalOnMissingBean(ClusterHostSupplierFactory.class)
    public ClusterHostSupplierFactory clusterHostSupplierFactory() {
      return ClusterHostSupplierFactory.nullSupplierFactory();
    }

    @Bean
    @ConditionalOnBean(Registry.class)
    @ConditionalOnProperty("cassandra.metrics.enabled")
    public KeyspaceConnectionPoolMonitorFactory spectatorConnectionPoolMonitor(Registry registry) {
      return SpectatorConnectionPoolMonitor.factory(registry);
    }

    @Bean
    @ConditionalOnMissingBean(KeyspaceConnectionPoolMonitorFactory.class)
    public KeyspaceConnectionPoolMonitorFactory countingConnectionPoolMonitor() {
      return KeyspaceConnectionPoolMonitorFactory.defaultFactory();
    }

    @Bean
    @ConfigurationProperties("cassandra")
    public ConnectionPoolConfiguration connectionPoolConfiguration(CassandraConfigurationProperties cassandraConfigurationProperties) {
        return new ConnectionPoolConfigurationImpl("cpConfig").setSeeds(cassandraConfigurationProperties.getHost());
    }

    @Bean
    public AstyanaxKeyspaceFactory keyspaceFactory(AstyanaxConfiguration config,
                                                   ConnectionPoolConfiguration poolConfig,
                                                   KeyspaceConnectionPoolMonitorFactory connectionPoolMonitorFactory,
                                                   ClusterHostSupplierFactory clusterHostSupplierFactory,
                                                   KeyspaceInitializer keyspaceInitializer) {
        return new DefaultAstyanaxKeyspaceFactory(config, poolConfig, connectionPoolMonitorFactory, clusterHostSupplierFactory, keyspaceInitializer);
    }

    @ConditionalOnExpression("${cassandra.embedded:true} and '${cassandra.host:127.0.0.1}' == '127.0.0.1'")
    @Bean
    @Primary
    @ConditionalOnBean(Keyspace.class)
    public KeyspaceInitializer embeddedCassandra(CassandraConfigurationProperties cassandraConfigurationProperties) {
        return new EmbeddedCassandraRunner(cassandraConfigurationProperties.getPort(), cassandraConfigurationProperties.getStoragePort(), cassandraConfigurationProperties.getHost());
    }

    @ConditionalOnMissingBean(KeyspaceInitializer.class)
    @Bean
    public KeyspaceInitializer noopKeyspaceInitializer() {
        return new KeyspaceInitializer() {
            @Override
            public void initKeyspace(Keyspace keyspace) throws ConnectionException {
                //noop
            }
        };
    }

    public static class EmbeddedCassandraRunner implements KeyspaceInitializer {
        private static final Logger log = LoggerFactory.getLogger(EmbeddedCassandraRunner.class);

        private final int port;
        private final int storagePort;
        private final String host;
        private EmbeddedCassandra embeddedCassandra;

        public EmbeddedCassandraRunner(int port, int storagePort, String host) {
            this.port = port;
            this.storagePort = storagePort;
            this.host = host;
        }

        @PostConstruct
        public void init() throws Exception {
            embeddedCassandra = new EmbeddedCassandra(new File("build/cassandra-test"), "TestCluster", port, storagePort);
            embeddedCassandra.start();
            log.info("Waiting for Embedded Cassandra instance...");
            Future<Object> waitForCassandraFuture = Executors.newSingleThreadExecutor().submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    while (true) {
                        try {
                            new Socket(host, port);
                            break;
                        } catch (IOException ignore) {
                            Thread.sleep(1000);
                        }
                    }
                    return null;
                }
            });
            waitForCassandraFuture.get(60, TimeUnit.SECONDS);
            log.info("Embedded cassandra started.");
        }

        @Override
        public void initKeyspace(Keyspace keyspace) throws ConnectionException {
            try {
                keyspace.describeKeyspace();
            } catch (ConnectionException e) {
                Map<String, Object> options = ImmutableMap.<String, Object>builder()
                    .put("name", keyspace.getKeyspaceName())
                    .put("strategy_class", "SimpleStrategy")
                    .put("strategy_options", ImmutableMap.of("replication_factor", "1"))
                    .build();
                keyspace.createKeyspace(options);
            }
        }

        @PreDestroy
        public void destroy() throws Exception {
            embeddedCassandra.stop();
        }
    }
}

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
import com.netflix.astyanax.AstyanaxConfiguration;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.ConnectionPoolConfiguration;
import com.netflix.astyanax.connectionpool.ConnectionPoolMonitor;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolType;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.test.EmbeddedCassandra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnMissingClass(name = {"com.netflix.cassandra.NFAstyanaxManager"})
@ConditionalOnClass(AstyanaxConfiguration.class)
public class AstyanaxComponents {

    @Bean
    public AstyanaxConfiguration astyanaxConfiguration() {
        return new AstyanaxConfigurationImpl()
                .setDiscoveryType(NodeDiscoveryType.RING_DESCRIBE)
                .setConnectionPoolType(ConnectionPoolType.TOKEN_AWARE)
                .setCqlVersion("3.0.0")
                .setTargetCassandraVersion("2.0");
    }

    @Bean
    public ConnectionPoolMonitor connectionPoolMonitor() {
        return new CountingConnectionPoolMonitor();
    }

    @Bean
    public ConnectionPoolConfiguration connectionPoolConfiguration(@Value("${cassandra.port:9160}") int port, @Value("${cassandra.host:127.0.0.1}") String seeds, @Value("${cassandra.maxConns:3}") int maxConns) {
        return new ConnectionPoolConfigurationImpl("cpConfig").setPort(port).setSeeds(seeds).setMaxConns(maxConns);
    }

    @Bean
    public AstyanaxKeyspaceFactory keyspaceFactory(AstyanaxConfiguration config,
                                                   ConnectionPoolConfiguration poolConfig,
                                                   ConnectionPoolMonitor poolMonitor,
                                                   KeyspaceInitializer keyspaceInitializer) {
        return new DefaultAstyanaxKeyspaceFactory(config, poolConfig, poolMonitor, keyspaceInitializer);
    }

    @ConditionalOnExpression("${cassandra.embedded:true} and '${cassandra.host:127.0.0.1}' == '127.0.0.1'")
    @Bean
    @ConditionalOnBean(Keyspace.class)
    public KeyspaceInitializer embeddedCassandra(@Value("${cassandra.port:9160}") int port,
                                                 @Value("${cassandra.storagePort:7000}") int storagePort,
                                                 @Value("${cassandra.host:127.0.0.1}") String host) {
        return new EmbeddedCassandraRunner(port, storagePort, host);
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

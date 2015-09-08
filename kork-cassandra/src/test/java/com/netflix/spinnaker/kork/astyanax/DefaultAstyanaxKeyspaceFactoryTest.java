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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.netflix.astyanax.AstyanaxConfiguration;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.ConnectionPoolConfiguration;
import com.netflix.astyanax.connectionpool.ConnectionPoolMonitor;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultAstyanaxKeyspaceFactoryTest {

    @BeforeClass
    public static void init() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        System.setProperty("cassandra.embedded", "false");
    }

    @AfterClass
    public static void cleanup() {
        System.clearProperty("cassandra.embedded");
    }

    @Test
    public void contextsAreCached() throws Exception {
        try(TestDAKF fac = new TestDAKF(new AstyanaxComponents())) {
            fac.getKeyspace("test", "test");
            Assert.assertEquals(1, fac.createCount.get());
            fac.getKeyspace("test", "test");
            Assert.assertEquals(1, fac.createCount.get());
        }
    }

    @Test
    public void contextsAreRemoved() throws Exception {
        try(TestDAKF fac = new TestDAKF(new AstyanaxComponents())) {
            fac.getKeyspace("test", "test");
            fac.getKeyspace("test", "test2");
            fac.shutdownContexts();
            Assert.assertEquals(2, fac.removeCount.get());
        }
    }

    private static class TestDAKF extends DefaultAstyanaxKeyspaceFactory implements Closeable {
        public final AtomicInteger createCount = new AtomicInteger();
        public final AtomicInteger removeCount = new AtomicInteger();

        public TestDAKF(AstyanaxComponents comp) {
            super(comp.astyanaxConfiguration(), comp.connectionPoolConfiguration(9160, "127.0.0.1", 3), comp.connectionPoolMonitor(), comp.noopKeyspaceInitializer());
        }

        @Override
        CacheLoader<DefaultAstyanaxKeyspaceFactory.KeyspaceKey, AstyanaxContext<Keyspace>> createCacheLoader(AstyanaxConfiguration astyanaxConfiguration,
                                                                                                             ConnectionPoolConfiguration connectionPoolConfiguration,
                                                                                                             ConnectionPoolMonitor connectionPoolMonitor,
                                                                                                             KeyspaceInitializer keyspaceInitializer) {
            final CacheLoader<KeyspaceKey, AstyanaxContext<Keyspace>> delegate = super.createCacheLoader(astyanaxConfiguration, connectionPoolConfiguration, connectionPoolMonitor, keyspaceInitializer);
            return new CacheLoader<KeyspaceKey, AstyanaxContext<Keyspace>>() {
                @Override
                public AstyanaxContext<Keyspace> load(KeyspaceKey key) throws Exception {
                    createCount.incrementAndGet();
                    return delegate.load(key);
                }
            };
        }

        @Override
        RemovalListener<KeyspaceKey, AstyanaxContext<Keyspace>> createRemovalListener() {
            final RemovalListener<KeyspaceKey, AstyanaxContext<Keyspace>> delegate = super.createRemovalListener();
            return new RemovalListener<KeyspaceKey, AstyanaxContext<Keyspace>>() {
                @Override
                public void onRemoval(RemovalNotification<KeyspaceKey, AstyanaxContext<Keyspace>> notification) {
                    removeCount.incrementAndGet();
                    delegate.onRemoval(notification);
                }
            };
        }

        @Override
        public void close() throws IOException {
            shutdownContexts();
        }
    }
}

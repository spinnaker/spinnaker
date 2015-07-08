/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.cassandra

import com.netflix.astyanax.Keyspace
import com.netflix.spinnaker.kork.astyanax.AstyanaxComponents
import com.netflix.spinnaker.kork.astyanax.AstyanaxComponents.EmbeddedCassandraRunner
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Abstract Cassandra spec
 */
@Ignore
class AbstractCassandraBackedSpec extends Specification {

    @Shared
    EmbeddedCassandraRunner runner

    @Shared
    Keyspace keyspace

    static int dbCount = 0

    void setupSpec() {
        int port = 9160
        int storagePort = 7000
        String host = '127.0.0.1'

        AstyanaxComponents components = new AstyanaxComponents()
        keyspace = components.keyspaceFactory(
            components.astyanaxConfiguration(),
            components.connectionPoolConfiguration(port, host, 3),
            components.connectionPoolMonitor()
        ).getKeyspace('workflow', 'test')

        try {
            new Socket(host, port)
        } catch (ConnectException e) {
            runner = new EmbeddedCassandraRunner(keyspace, port, storagePort, host)
            runner.init()
        }
    }
}

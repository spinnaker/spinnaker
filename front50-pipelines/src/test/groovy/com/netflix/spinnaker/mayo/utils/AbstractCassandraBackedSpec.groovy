package com.netflix.spinnaker.mayo.utils

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

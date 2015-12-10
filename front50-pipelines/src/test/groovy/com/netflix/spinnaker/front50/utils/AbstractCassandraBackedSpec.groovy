package com.netflix.spinnaker.front50.utils

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl
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
        try {
            new Socket(host, port)
        } catch (ConnectException e) {
            runner = new EmbeddedCassandraRunner(port, storagePort, host)
            runner.init()
        }

        AstyanaxComponents components = new AstyanaxComponents()
        ConnectionPoolConfigurationImpl poolCfg = components.connectionPoolConfiguration()
        poolCfg.setPort(port)
        poolCfg.setSeeds(host)

        keyspace = components.keyspaceFactory(
            components.astyanaxConfiguration(components.cassandraAsyncExecutor(5)),
            poolCfg,
            components.countingConnectionPoolMonitor(),
            components.clusterHostSupplierFactory(),
            runner ?: components.noopKeyspaceInitializer()).getKeyspace('workflow', 'test')

    }
}

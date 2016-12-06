package com.netflix.spinnaker.front50.utils

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.spinnaker.kork.astyanax.AstyanaxComponents
import com.netflix.spinnaker.kork.astyanax.CassandraConfigurationProperties


public class CassandraTestHelper {
  AstyanaxComponents.EmbeddedCassandraRunner runner
  Keyspace keyspace

  static int dbCount = 0

  CassandraTestHelper() {
    int port = 9160
    int storagePort = 7000
    String host = '127.0.0.1'

    try {
      new Socket(host, port)
    } catch (ConnectException e) {
      runner = new AstyanaxComponents.EmbeddedCassandraRunner(port, storagePort, host)
      runner.init()
    }

    AstyanaxComponents components = new AstyanaxComponents()
    def ccp = new CassandraConfigurationProperties(host: host, port: port, storagePort: storagePort)
    ConnectionPoolConfigurationImpl poolCfg = components.connectionPoolConfiguration(ccp)
    poolCfg.setPort(port)
    poolCfg.setSeeds(host)

    keyspace = components.keyspaceFactory(
        components.astyanaxConfiguration(components.cassandraAsyncExecutor(ccp)),
        poolCfg,
        components.countingConnectionPoolMonitor(),
        components.clusterHostSupplierFactory(),
        runner ?: components.noopKeyspaceInitializer()).getKeyspace('workflow', 'test')
  }
}

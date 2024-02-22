/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.sql.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.pool.HikariProxyConnection;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.jooq.impl.DataSourceConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.ConnectionProxy;

class TransactionIsolationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "sql.enabled=true",
              "sql.connectionPools.default.default=true",
              "sql.connectionPools.default.jdbcUrl=jdbc:h2:mem:test",
              "sql.connectionPools.default.dialect=H2",
              "sql.migration.jdbcUrl=jdbc:h2:mem:test",
              "sql.migration.dialect=H2",
              "sql.migration.duplicateFileMode=WARN")
          .withConfiguration(UserConfigurations.of(DefaultSqlConfiguration.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testTransactionIsolation() {
    runner.run(
        ctx -> {
          DataSourceConnectionProvider dataSourceConnectionProvider =
              ctx.getBean(DataSourceConnectionProvider.class);
          testTargetConnection(
              dataSourceConnectionProvider,
              (DatabaseMetaData metadata) -> {
                when(metadata.supportsTransactionIsolationLevel(anyInt())).thenReturn(true);
              },
              (Connection target) -> {
                // Verify that the default behavior is to set the
                // transaction isolation level to
                // Connection.TRANSACTION_READ_COMMITTED
                ArgumentCaptor<Integer> transactionIsolationLevelCaptor =
                    ArgumentCaptor.forClass(Integer.class);
                verify(target).setTransactionIsolation(transactionIsolationLevelCaptor.capture());
                assertThat(transactionIsolationLevelCaptor.getValue())
                    .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
              });
        });
  }

  @Test
  void testTransactionIsolationUnsupported() {
    runner.run(
        ctx -> {
          DataSourceConnectionProvider dataSourceConnectionProvider =
              ctx.getBean(DataSourceConnectionProvider.class);
          testTargetConnection(
              dataSourceConnectionProvider,
              (DatabaseMetaData metadata) -> {
                when(metadata.supportsTransactionIsolationLevel(anyInt())).thenReturn(false);
              },
              (Connection target) -> {
                // Verify that when the transaction isolation level is unsupported,
                // we don't set the transaction isolation level.
                verify(target, never()).setTransactionIsolation(anyInt());
              });
        });
  }

  @Test
  void testSetTransactionIsolationFalse() {
    runner
        .withPropertyValues("sql.setTransactionIsolation=false")
        .run(
            ctx -> {
              DataSourceConnectionProvider dataSourceConnectionProvider =
                  ctx.getBean(DataSourceConnectionProvider.class);
              testTargetConnection(
                  dataSourceConnectionProvider,
                  (DatabaseMetaData metadata) -> {
                    when(metadata.supportsTransactionIsolationLevel(anyInt())).thenReturn(true);
                  },
                  (Connection target) -> {
                    // Verify that when setTransactionIsolation is false,
                    // we never set the transaction isolation level.
                    verify(target, never()).setTransactionIsolation(anyInt());
                  });
            });
  }

  @Test
  void testSetTransactionIsolation() {
    runner
        .withPropertyValues(
            "sql.setTransactionIsolation=true",
            "sql.transactionIsolation=" + Connection.TRANSACTION_NONE) // arbitrary
        .run(
            ctx -> {
              DataSourceConnectionProvider dataSourceConnectionProvider =
                  ctx.getBean(DataSourceConnectionProvider.class);
              testTargetConnection(
                  dataSourceConnectionProvider,
                  (DatabaseMetaData metadata) -> {
                    when(metadata.supportsTransactionIsolationLevel(anyInt())).thenReturn(true);
                  },
                  (Connection target) -> {
                    // Verify that we set the transaction isolation level to
                    // sql.transactionIsolation
                    ArgumentCaptor<Integer> transactionIsolationLevelCaptor =
                        ArgumentCaptor.forClass(Integer.class);
                    verify(target)
                        .setTransactionIsolation(transactionIsolationLevelCaptor.capture());
                    assertThat(transactionIsolationLevelCaptor.getValue())
                        .isEqualTo(Connection.TRANSACTION_NONE);
                  });
            });
  }

  /**
   * Acquire a connection from a DataSourceConnectionProvider using mock DatabaseMetaData, and then
   * invoke a lambda on the resulting target connection.
   *
   * @param dataSourceConnectionProvider a DataSourceConnectionProvider
   * @param metadataSetup lambda to set up a mock DatabaseMetadata object (optional)
   * @param assertions the lambda to invoke on the resulting target connection
   */
  private void testTargetConnection(
      DataSourceConnectionProvider dataSourceConnectionProvider,
      SqlConsumer<DatabaseMetaData> metadataSetup,
      SqlConsumer<Connection> assertions)
      throws SQLException {
    // To run assertions (e.g. mockito verify) we need a mock.  Configure
    // mockito to return a mock whenever a Connection constructor is
    // called...except Connection is an interface, and we can only mock concrete
    // classes.  It happens that we're dealing with HikariProxyConnection here.
    try (MockedConstruction<HikariProxyConnection> mocked =
        Mockito.mockConstruction(
            HikariProxyConnection.class,
            (mock, context) -> {
              DatabaseMetaData databaseMetaData = Mockito.mock(DatabaseMetaData.class);
              if (metadataSetup != null) {
                metadataSetup.accept(databaseMetaData);
              }
              when(mock.getMetaData()).thenReturn(databaseMetaData);
            })) {
      // It takes diving into yet more implementation details to verify what
      // happened.  The return value from dataSourceConnectionProvider.acquire
      // is a proxy.  The target connection is the mock.
      Connection target = getTargetConnection(dataSourceConnectionProvider.acquire());
      assertions.accept(target);
    }
  }

  /** Retrieve the target connection of a connection proxy */
  private Connection getTargetConnection(Connection connectionProxy) {
    assertThat(connectionProxy).isInstanceOf(ConnectionProxy.class);
    return ((ConnectionProxy) connectionProxy).getTargetConnection();
  }

  /** Like Consumer, but throws SQLException */
  private interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
  }
}

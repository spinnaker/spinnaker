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

import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.jooq.impl.DataSourceConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.ConnectionProxy;

class TransactionIsolationMysqlTest {

  static SqlTestUtil.TestDatabase mysql = SqlTestUtil.initTcMysqlDatabase();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "sql.enabled=true",
              "sql.connectionPools.default.default=true",
              "sql.connectionPools.default.jdbcUrl=" + SqlTestUtil.tcJdbcUrl,
              "sql.migration.jdbcUrl=" + SqlTestUtil.tcJdbcUrl,
              "sql.migration.duplicateFileMode=WARN")
          .withConfiguration(UserConfigurations.of(DefaultSqlConfiguration.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  // Note that
  // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html
  // mentions the alwaysSendSetIsolation property which defaults to true.  The
  // code is here:
  // https://github.com/mysql/mysql-connector-j/blob/8.0.31/src/main/user-impl/java/com/mysql/cj/jdbc/ConnectionImpl.java#L2208-L2220.
  //
  // Use both values to verify that it doesn't affect the behavior, only the performance.
  @ParameterizedTest(name = "testSetTransactionIsolationFalse alwaysSendSetIsolation = {0}")
  @ValueSource(booleans = {true, false})
  void testSetTransactionIsolationFalseGlobal(boolean alwaysSendSetIsolation) {
    runner
        .withPropertyValues(
            "sql.setTransactionIsolation=false",
            // use root so SET GLOBAL TRANSACTION ISOLATION LEVEL works
            "sql.connectionPools.default.jdbcUrl="
                + SqlTestUtil.tcJdbcUrl
                + "?user=root&alwaysSendSetIsolation="
                + alwaysSendSetIsolation
                // See
                // https://java.testcontainers.org/modules/databases/jdbc/#using-a-classpath-init-script
                + "&TC_INITSCRIPT=mysql-set-global-transaction-isolation.sql")
        .run(
            ctx -> {
              DataSourceConnectionProvider dataSourceConnectionProvider =
                  ctx.getBean(DataSourceConnectionProvider.class);
              try (Connection connection = dataSourceConnectionProvider.acquire();
                  Connection target = getTargetConnection(connection);
                  Statement stmt = target.createStatement()) {

                // Even though we configure kork to not set transaction
                // isolation, the jdbc driver still does.  Even if we set the
                // alwaysSendSetIsolation to false in the driver (see
                // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html),
                // it still sends it if its transaction isolation level is
                // different than the database's.
                //
                // It happens that the default transaction isolation value in
                // both mysql and the jdbc driver is REPEATABLE READ.  See
                // https://dev.mysql.com/doc/refman/5.7/en/innodb-consistent-read.html
                // and the the default value of
                // com.mysql.cj.jdbc.ConnectionImpl's isolationLevel member.
                //
                // That's why we set (via
                // mysql-set-global-transaction-isolation.sql) the default value
                // from the mysql side to something different than REPEATABLE
                // READ.

                // This demonstrates the value we set in mysql
                verifyGlobalTransactionIsolationLevel(stmt, "READ-UNCOMMITTED");

                // This demonstrates that the default value from the jdbc driver takes
                // precedence over the value set in mysql.
                verifySessionTransactionIsolationLevel(stmt, "REPEATABLE-READ");

                // To further explore, change a value on the database side.
                // Let's start with changing the global setting.

                // See https://dev.mysql.com/doc/refman/5.7/en/set-transaction.html.
                assertThat(
                        stmt.executeUpdate("SET GLOBAL TRANSACTION ISOLATION LEVEL SERIALIZABLE"))
                    .isEqualTo(0);

                // Verify that the global transaction isolation level has changed
                verifyGlobalTransactionIsolationLevel(stmt, "SERIALIZABLE");

                // Verify that the session transaction isolation level hasn't
                // changed (yet).  From
                // https://dev.mysql.com/doc/refman/5.7/en/set-transaction.html,
                //
                // With the GLOBAL keyword:
                // - The statement applies globally for all subsequent sessions.
                //
                // With the SESSION keyword:
                // - The statement applies to all subsequent transactions performed within the
                // current session.
                verifySessionTransactionIsolationLevel(stmt, "REPEATABLE-READ");
              }

              // Create a new connection to see what the transaction isolation
              // levels are.
              try (Connection connection = dataSourceConnectionProvider.acquire();
                  Connection target = getTargetConnection(connection);
                  Statement stmt = target.createStatement()) {
                // Expect the global value we set in the previous session to persist
                verifyGlobalTransactionIsolationLevel(stmt, "SERIALIZABLE");

                // Expect the jdbc driver's default for the session since kork
                // isn't setting it.  Happily the jdbc driver's default
                // (repeatable read) is different than kork's default (read
                // committed), and also different than what we set the global
                // value to.  This shows that the global setting isn't important.
                verifySessionTransactionIsolationLevel(stmt, "REPEATABLE-READ");

                // Now, let's change the session transaction isolation level and
                // see what happens.
                assertThat(
                        stmt.executeUpdate(
                            "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED"))
                    .isEqualTo(0);

                // Verify that the global transaction isolation level hasn't changed
                verifyGlobalTransactionIsolationLevel(stmt, "SERIALIZABLE");

                // Verify that the session transaction isolation level has changed
                verifySessionTransactionIsolationLevel(stmt, "READ-COMMITTED");
              }

              // Create another new connection to see what the transaction isolation levels are.
              try (Connection connection = dataSourceConnectionProvider.acquire();
                  Connection target = getTargetConnection(connection);
                  Statement stmt = target.createStatement()) {
                // Expect the global value we set in the first session to persist
                verifyGlobalTransactionIsolationLevel(stmt, "SERIALIZABLE");

                // Note that jdbc driver's default is no longer relevant now
                // that we've set something in the database.
                verifySessionTransactionIsolationLevel(stmt, "READ-COMMITTED");
              }
            });
  }

  @Test
  void testSetTransactionIsolationFalseSession() {
    runner
        .withPropertyValues(
            "sql.setTransactionIsolation=false",
            "sql.connectionPools.default.jdbcUrl="
                + SqlTestUtil.tcJdbcUrl
                // See
                // https://java.testcontainers.org/modules/databases/jdbc/#using-a-classpath-init-script
                + "&TC_INITSCRIPT=mysql-set-session-transaction-isolation.sql")
        .run(
            ctx -> {
              DataSourceConnectionProvider dataSourceConnectionProvider =
                  ctx.getBean(DataSourceConnectionProvider.class);
              try (Connection connection = dataSourceConnectionProvider.acquire();
                  Connection target = getTargetConnection(connection);
                  Statement stmt = target.createStatement()) {

                // Even though we configure kork to not set transaction
                // isolation, the jdbc driver still does.  Even if we set the
                // alwaysSendSetIsolation to false in the driver (see
                // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html),
                // it still sends it if its transaction isolation level is
                // different than the database's.
                //
                // It happens that the default transaction isolation value in
                // both mysql and the jdbc driver is REPEATABLE READ.  See
                // https://dev.mysql.com/doc/refman/5.7/en/innodb-consistent-read.html
                // and the default value from the jdbc connection (i.e. the
                // default value of com.mysql.cj.jdbc.ConnectionImpl's
                // isolationLevel member).
                //
                // This test sets (via
                // mysql-set-session-transaction-isolation.sql) the default value
                // from the mysql side to something different than REPEATABLE
                // READ.

                // This demonstrates the default value in mysql
                verifyGlobalTransactionIsolationLevel(stmt, "REPEATABLE-READ");

                // This demonstrates the setting in the jdbc driver takes
                // precedence over the value we set in mysql.
                verifySessionTransactionIsolationLevel(stmt, "REPEATABLE-READ");
              }
            });
  }

  @Test
  void testSetTransactionIsolationTrue() {
    runner
        .withPropertyValues(
            "sql.setTransactionIsolation=true",
            "sql.transactionIsolation=" + Connection.TRANSACTION_SERIALIZABLE) // arbitrary
        .run(
            ctx -> {
              DataSourceConnectionProvider dataSourceConnectionProvider =
                  ctx.getBean(DataSourceConnectionProvider.class);
              try (Connection connection = dataSourceConnectionProvider.acquire();
                  Connection target = getTargetConnection(connection);
                  Statement stmt = target.createStatement()) {

                // Verify that the value we configured is actually in use.
                verifySessionTransactionIsolationLevel(stmt, "SERIALIZABLE");

                // Change the session transaction isolation level in the
                // database.  Expect new sessions to still use the value we
                // configured in kork.
                assertThat(
                        stmt.executeUpdate(
                            "SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"))
                    .isEqualTo(0);

                // Verify that the session transaction isolation level has changed
                verifySessionTransactionIsolationLevel(stmt, "READ-UNCOMMITTED");
              }

              // Create a new connection to see what the session transaction
              // isolation level is.
              try (Connection connection = dataSourceConnectionProvider.acquire();
                  Connection target = getTargetConnection(connection);
                  Statement stmt = target.createStatement()) {
                // Expect the value we configured in kork.
                verifySessionTransactionIsolationLevel(stmt, "SERIALIZABLE");
              }
            });
  }

  /** Retrieve the target connection of a connection proxy */
  private Connection getTargetConnection(Connection connectionProxy) {
    assertThat(connectionProxy).isInstanceOf(ConnectionProxy.class);
    return ((ConnectionProxy) connectionProxy).getTargetConnection();
  }

  private void verifyGlobalTransactionIsolationLevel(Statement stmt, String expectedLevel)
      throws SQLException {
    try (ResultSet global_tx_isolation_rs = stmt.executeQuery("SELECT @@global.tx_isolation")) {
      assertThat(global_tx_isolation_rs.next()).isTrue();
      assertThat(global_tx_isolation_rs.getString("@@global.tx_isolation"))
          .isEqualTo(expectedLevel);
      assertThat(global_tx_isolation_rs.isLast()).isTrue();
    }
  }

  private void verifySessionTransactionIsolationLevel(Statement stmt, String expectedLevel)
      throws SQLException {
    try (ResultSet session_tx_isolation_rs = stmt.executeQuery("SELECT @@tx_isolation")) {
      assertThat(session_tx_isolation_rs.next()).isTrue();
      assertThat(session_tx_isolation_rs.getString("@@tx_isolation")).isEqualTo(expectedLevel);
      assertThat(session_tx_isolation_rs.isLast()).isTrue();
    }
  }
}

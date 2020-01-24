/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.sql.test;

import static org.jooq.SQLDialect.H2;
import static org.jooq.conf.RenderNameStyle.AS_IS;
import static org.jooq.impl.DSL.currentSchema;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Comparator;
import liquibase.ContextExpression;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.SetupException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.testcontainers.containers.MySQLContainer;

public class SqlTestUtil {

  public static String tcJdbcUrl = "jdbc:tc:mysql:5.7.22://somehostname:someport/somedb";

  public static TestDatabase initDatabase() {
    return initDatabase("jdbc:h2:mem:test;MODE=MYSQL");
  }

  public static TestDatabase initPreviousDatabase() {
    return initDatabase("jdbc:h2:mem:test_previous;MODE=MYSQL");
  }

  public static TestDatabase initTcMysqlDatabase() {
    // host, port, and db name are ignored with the jdbcUrl method of TC initialization and
    // overridden to "test" by the driver.
    return initDatabase(tcJdbcUrl, SQLDialect.MYSQL);
  }

  @Deprecated
  public static TestDatabase initPreviousTcMysqlDatabase() {
    MySQLContainer container =
        new MySQLContainer("mysql:5.7.22")
            .withDatabaseName("previous")
            .withUsername("test")
            .withPassword("test");

    container.start();

    String jdbcUrl =
        String.format(
            "%s?user=%s&password=%s",
            container.getJdbcUrl(), container.getUsername(), container.getPassword());

    return initDatabase(jdbcUrl, SQLDialect.MYSQL, "previous");
  }

  public static TestDatabase initDualTcMysqlDatabases() {
    MySQLContainer container =
        new MySQLContainer("mysql:5.7.22")
            .withDatabaseName("current")
            .withUsername("test")
            .withPassword("test");
    container.start();

    String rootJdbcUrl =
        String.format(
            "%s?user=%s&password=%s", container.getJdbcUrl(), "root", container.getPassword());

    try {
      Connection rootCon = DriverManager.getConnection(rootJdbcUrl);
      rootCon.createStatement().executeUpdate("create database previous");
      rootCon.createStatement().executeUpdate("grant all privileges on previous.* to 'test'@'%'");
      rootCon.close();
    } catch (SQLException e) {
      throw new RuntimeException("Error setting up testcontainer database", e);
    }

    String currentJdbcUrl =
        String.format(
            "%s?user=%s&password=%s",
            container.getJdbcUrl(), container.getUsername(), container.getPassword());

    String previousJdbcUrl = currentJdbcUrl.replace("/current", "/previous");

    TestDatabase currentTDB = initDatabase(currentJdbcUrl, SQLDialect.MYSQL, "current");
    TestDatabase previousTDB = initDatabase(previousJdbcUrl, SQLDialect.MYSQL, "previous");

    return new TestDatabase(
        currentTDB.dataSource,
        currentTDB.context,
        currentTDB.liquibase,
        previousTDB.dataSource,
        previousTDB.context,
        previousTDB.liquibase);
  }

  public static TestDatabase initDatabase(String jdbcUrl) {
    return initDatabase(jdbcUrl, H2);
  }

  public static TestDatabase initDatabase(String jdbcUrl, SQLDialect dialect) {
    return initDatabase(jdbcUrl, dialect, "test");
  }

  public static TestDatabase initDatabase(String jdbcUrl, SQLDialect dialect, String dbName) {
    HikariConfig cpConfig = new HikariConfig();
    cpConfig.setJdbcUrl(jdbcUrl);
    cpConfig.setMaximumPoolSize(5);
    HikariDataSource dataSource = new HikariDataSource(cpConfig);

    DefaultConfiguration config = new DefaultConfiguration();
    config.set(new DataSourceConnectionProvider(dataSource));
    config.setSQLDialect(dialect);

    if (dialect == H2) {
      config.settings().withRenderNameStyle(AS_IS);
    }

    DSLContext context = new DefaultDSLContext(config);

    Liquibase migrate;
    try {
      DatabaseChangeLog changeLog = new DatabaseChangeLog();

      changeLog.setChangeLogParameters(
          new ChangeLogParameters(
              DatabaseFactory.getInstance()
                  .findCorrectDatabaseImplementation(
                      new JdbcConnection(dataSource.getConnection()))));

      changeLog.includeAll(
          "db/changelog/",
          false,
          null,
          false,
          Comparator.comparing(String::toString),
          new ClassLoaderResourceAccessor(),
          new ContextExpression(),
          new LabelExpression(),
          false);

      migrate =
          new Liquibase(
              changeLog,
              new ClassLoaderResourceAccessor(),
              DatabaseFactory.getInstance()
                  .findCorrectDatabaseImplementation(
                      new JdbcConnection(dataSource.getConnection())));
    } catch (DatabaseException | SQLException | SetupException e) {
      throw new DatabaseInitializationFailed(e);
    }

    try {
      migrate.update(dbName);
    } catch (LiquibaseException e) {
      throw new DatabaseInitializationFailed(e);
    }

    return new TestDatabase(dataSource, context, migrate);
  }

  public static void cleanupDb(DSLContext context) {
    String schema = context.select(currentSchema()).fetch().getValue(0, 0).toString();

    GlobalConfiguration configuration =
        LiquibaseConfiguration.getInstance().getConfiguration(GlobalConfiguration.class);

    context.execute("set foreign_key_checks=0");
    context.meta().getTables().stream()
        .filter(
            table ->
                table.getSchema().getName().equals(schema)
                    && !table.getName().equals(configuration.getDatabaseChangeLogTableName())
                    && !table.getName().equals(configuration.getDatabaseChangeLogLockTableName()))
        .forEach(
            table -> {
              context.truncate(table.getName()).execute();
            });
    context.execute("set foreign_key_checks=1");
  }

  public static class TestDatabase implements Closeable {
    public final HikariDataSource dataSource;
    public final HikariDataSource previousDataSource;
    public final DSLContext context;
    public final DSLContext previousContext;
    public final Liquibase liquibase;
    public final Liquibase previousLiquibase;

    TestDatabase(HikariDataSource dataSource, DSLContext context, Liquibase liquibase) {
      this.dataSource = dataSource;
      this.context = context;
      this.liquibase = liquibase;
      this.previousDataSource = null;
      this.previousContext = null;
      this.previousLiquibase = null;
    }

    TestDatabase(
        HikariDataSource dataSource,
        DSLContext context,
        Liquibase liquibase,
        HikariDataSource previousDataSource,
        DSLContext previousContext,
        Liquibase previousLiquibase) {
      this.dataSource = dataSource;
      this.context = context;
      this.liquibase = liquibase;
      this.previousDataSource = previousDataSource;
      this.previousContext = previousContext;
      this.previousLiquibase = previousLiquibase;
    }

    @Override
    public void close() {
      dataSource.close();
    }
  }

  private static class DatabaseInitializationFailed extends RuntimeException {
    DatabaseInitializationFailed(Throwable cause) {
      super(cause);
    }
  }
}

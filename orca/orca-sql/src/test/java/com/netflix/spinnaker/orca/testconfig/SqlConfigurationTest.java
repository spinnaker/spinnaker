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

package com.netflix.spinnaker.orca.testconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.config.SqlConfiguration;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

class SqlConfigurationTest {

  private static SqlTestUtil.TestDatabase database = SqlTestUtil.initTcMysqlDatabase();

  // Without .withAllowBeanDefinitionOverriding(true), this fails with an error:
  //
  // org.springframework.beans.factory.support.BeanDefinitionOverrideException:
  // Invalid bean definition with name 'liquibase' defined in
  // com.netflix.spinnaker.config.SqlConfiguration: Cannot register bean
  // definition [Root bean: class [null]; scope=; abstract=false; lazyInit=null;
  // autowireMode=3; dependencyCheck=0; autowireCandidate=true; primary=false;
  // factoryBeanName=sqlConfiguration; factoryMethodName=liquibase;
  // initMethodName=null; destroyMethodName=(inferred); defined in
  // com.netflix.spinnaker.config.SqlConfiguration] for bean 'liquibase': There
  // is already [Root bean: class [null]; scope=; abstract=false; lazyInit=null;
  // autowireMode=3; dependencyCheck=0; autowireCandidate=true; primary=false;
  // factoryBeanName=com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration;
  // factoryMethodName=liquibase; initMethodName=null;
  // destroyMethodName=(inferred); defined in
  // com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration] bound.
  //
  // Even though DefaultSqlConfiguration has
  //
  // @Bean
  // @ConditionalOnMissingBean(SpringLiquibase::class)
  // fun liquibase(properties: SqlProperties, @Value("\${sql.read-only:false}") sqlReadOnly:
  // Boolean): SpringLiquibase =
  //   SpringLiquibaseProxy(properties.migration, sqlReadOnly)
  //
  // which makes sense if DefaultSqlConfiguration gets processed first...
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "sql.enabled=true",
              "sql.connectionPools.default.default=true",
              "sql.connectionPools.default.jdbcUrl=" + SqlTestUtil.tcJdbcUrl,
              "sql.migration.jdbcUrl=" + SqlTestUtil.tcJdbcUrl)
          .withAllowBeanDefinitionOverriding(true)
          .withConfiguration(UserConfigurations.of(SqlConfiguration.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testDataSourceWithDefaultConnectionPool() {
    runner.run(
        ctx -> {
          DataSource dataSource = ctx.getBean(DataSource.class);
          assertThat(dataSource).isNotNull();
          // with only one connection pool configured, we get a "plain" data source with no routing.
          assertThat(dataSource instanceof AbstractRoutingDataSource).isFalse();
        });
  }

  @Test
  void testDataSourceWithReadConnectionPool() {
    runner
        .withPropertyValues("sql.connectionPools.read.jdbcUrl=" + SqlTestUtil.tcJdbcUrl)
        .run(
            ctx -> {
              DataSource dataSource = ctx.getBean(DataSource.class);
              assertThat(dataSource).isNotNull();
              // with multiple connection pools configured, we get a routing data source
              assertThat(dataSource instanceof AbstractRoutingDataSource).isTrue();
            });
  }
}

/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.sql.pipeline.persistence;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.SqlConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import de.huxhorn.sulky.ulid.ULID;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {
      SqlConfiguration.class,
      SqlExecutionRepositoryReadReplicaTest.SqlExecutionRepositoryReadReplicaTestConfiguration.class
    })
@TestPropertySource(
    properties = {
      "sql.enabled=true",
      "execution-repository.sql.enabled=true",
      "sql.connectionPools.default.default=true"
    })
public class SqlExecutionRepositoryReadReplicaTest {
  @Autowired ExecutionRepository executionRepository;

  @MockBean OkHttpClientProvider okHttpClientProvider;
  @MockBean Clock clock;
  @Autowired DataSourceConnectionProvider dataSourceConnectionProvider;
  @Autowired ObjectMapper mapper;
  private static final String primaryInstanceUrl = SqlTestUtil.tcJdbcUrl;
  private static final String readReplicaUrl = SqlTestUtil.tcJdbcUrl + "replica";
  private static SqlTestUtil.TestDatabase primaryInstance =
      SqlTestUtil.initDatabase(primaryInstanceUrl, SQLDialect.MYSQL);
  private static SqlTestUtil.TestDatabase readReplica =
      SqlTestUtil.initDatabase(readReplicaUrl, SQLDialect.MYSQL);
  private static final ULID ID_GENERATOR = new ULID();
  private final String pipelineId = ID_GENERATOR.nextULID();
  private PipelineExecution defaultPoolPipelineExecution =
      new PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineId, "myapp");
  private PipelineExecution readPoolPipelineExecution =
      new PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineId, "myapp");

  @DynamicPropertySource
  static void registerSQLProperties(DynamicPropertyRegistry registry) {
    registry.add("sql.connectionPools.default.jdbcUrl", () -> primaryInstanceUrl);
    registry.add("sql.migration.jdbcUrl", () -> primaryInstanceUrl);
    registry.add("sql.connectionPools.read.jdbcUrl", () -> readReplicaUrl);
  }

  @BeforeEach
  void initDatabases() throws SQLException {
    // Set some fields in the PipelineExecutions to differentiate them from each other
    defaultPoolPipelineExecution.setName("defaultPoolPipelineExecution");
    readPoolPipelineExecution.setName("readPoolPipelineExecution");

    String insertSql =
        "INSERT INTO pipelines (id, application, build_time, canceled, updated_at, body) VALUES (?, ?, ?, ?, ?, ?)";
    // populate primary instance
    try (Connection connection = primaryInstance.dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(insertSql)) {
      String defaultPoolBody = mapper.writeValueAsString(defaultPoolPipelineExecution);
      stmt.setString(1, pipelineId);
      stmt.setString(2, defaultPoolPipelineExecution.getApplication());
      stmt.setLong(3, Instant.now().toEpochMilli());
      stmt.setBoolean(4, defaultPoolPipelineExecution.isCanceled());
      stmt.setLong(5, Instant.now().toEpochMilli());
      stmt.setString(6, defaultPoolBody);
      stmt.executeUpdate();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    // populate read replica
    try (Connection connection = readReplica.dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(insertSql)) {
      String readPoolBody = mapper.writeValueAsString(readPoolPipelineExecution);
      stmt.setString(1, pipelineId);
      stmt.setString(2, readPoolPipelineExecution.getApplication());
      stmt.setLong(3, Instant.now().toEpochMilli());
      stmt.setBoolean(4, readPoolPipelineExecution.isCanceled());
      stmt.setLong(5, Instant.now().toEpochMilli());
      stmt.setString(6, readPoolBody);
      stmt.executeUpdate();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testRetrieveRequireLatestVersion() {
    PipelineExecution execution =
        executionRepository.retrieve(ExecutionType.PIPELINE, pipelineId, true);
    assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());

    execution = executionRepository.retrieve(ExecutionType.PIPELINE, pipelineId, false);
    assertThat(execution.getName()).isEqualTo(readPoolPipelineExecution.getName());
  }

  @TestConfiguration
  static class SqlExecutionRepositoryReadReplicaTestConfiguration {
    @Bean
    ObjectMapper orcaObjectMapper() {
      return OrcaObjectMapper.getInstance();
    }
  }
}

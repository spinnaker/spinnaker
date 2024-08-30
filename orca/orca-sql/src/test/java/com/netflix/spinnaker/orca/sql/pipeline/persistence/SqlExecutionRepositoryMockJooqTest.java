/*
 * Copyright 2024 Salesforce, Inc.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.jooq.impl.DSL.field;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.ExecutionCompressionProperties;
import com.netflix.spinnaker.kork.sql.config.RetryProperties;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.persistence.ReplicationLagAwareRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/** Mocking jooq enables verification of exception handling */
public class SqlExecutionRepositoryMockJooqTest {

  private static final String partitionName = "testPartition";
  private static final String poolName = "poolName";
  private static final String readPoolName = "readPoolName";
  private static final SQLException sqlException = new SQLException("arbitrary");

  private MockDataProvider provider = new ExceptionThrowingProvider();
  private MockConnection connection = new MockConnection(provider);
  private DSLContext dslContext = DSL.using(connection);

  private ObjectMapper objectMapper = new ObjectMapper();
  private AbstractRoutingDataSource abstractRoutingDataSource =
      mock(AbstractRoutingDataSource.class);
  private SqlExecutionRepository sqlExecutionRepository;
  private ExecutionCompressionProperties executionCompressionPropertiesEnabled =
      new ExecutionCompressionProperties();
  private ReplicationLagAwareRepository replicationLagAwareRepository =
      mock(ReplicationLagAwareRepository.class);
  private Registry registry = new DefaultRegistry();

  /** See https://www.jooq.org/doc/latest/manual/sql-execution/mocking-connection/ for background */
  private static class ExceptionThrowingProvider implements MockDataProvider {
    @Override
    public MockResult[] execute(MockExecuteContext ctx) throws SQLException {
      // mock a real result for partition name so the init block of SqlExecutionRepository can
      // succeed.
      if (ctx.sql().equals("select * from partition_name")) {
        DSLContext create = DSL.using(SQLDialect.DEFAULT);
        MockResult[] mockResult = new MockResult[1];
        Field idField = field("id", Integer.class);
        Field nameField = field("name", String.class);
        Result<Record2<Integer, String>> result = create.newResult(idField, nameField);
        result.add(create.newRecord(idField, nameField).values(1, partitionName));
        mockResult[0] = new MockResult(1, result);
        return mockResult;
      }

      throw sqlException;
    }
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    // Configure a data source for the read pool, so SqlExecutionRepository
    // actually uses it.
    when(abstractRoutingDataSource.getResolvedDataSources())
        .thenReturn(Map.of(readPoolName, mock(DataSource.class)));

    sqlExecutionRepository =
        new SqlExecutionRepository(
            partitionName,
            dslContext,
            objectMapper,
            new RetryProperties(),
            10,
            100,
            poolName,
            readPoolName,
            null /* interlink */,
            List.of(), /* executionRepositoryListeners */
            executionCompressionPropertiesEnabled, // arbitrary
            false /* pipelineRefEnabled */,
            abstractRoutingDataSource,
            Optional.of(replicationLagAwareRepository),
            registry);
  }

  @Test
  void testExceptionFromReadPool() {
    assertThatThrownBy(
            () -> sqlExecutionRepository.retrieve(ExecutionType.PIPELINE, "any-pipeline-id", true))
        .isInstanceOf(DataAccessException.class)
        .hasCause(sqlException);
    validateReadPoolMetricsOnFailure(ExecutionMapperResultCode.FAILURE);
  }

  void validateReadPoolMetricsOnFailure(ExecutionMapperResultCode resultCode) {
    assertThat(
            registry
                .counter("executionRepository.sql.readPool.retrieveSucceeded", "numAttempts", "1")
                .count())
        .isEqualTo(0);
    assertThat(
            registry
                .counter(
                    "executionRepository.sql.readPool.retrieveFailed",
                    "result_code",
                    resultCode.toString())
                .count())
        .isEqualTo(1);
    assertThat(registry.counter("executionRepository.sql.readPool.retrieveTotalAttempts").count())
        .isEqualTo(1);
  }
}

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.config.CompressionType;
import com.netflix.spinnaker.config.ExecutionCompressionProperties;
import com.netflix.spinnaker.config.SqlConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ReadReplicaRequirement;
import com.netflix.spinnaker.orca.pipeline.persistence.ReplicationLagAwareRepository;
import de.huxhorn.sulky.ulid.ULID;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
      "execution-repository.sql.read-replica.enabled=true",
      "sql.connectionPools.default.default=true",
      "sql.retries.transactions.maxRetries=1"
    })
public class SqlExecutionRepositoryReadReplicaTest {
  @Autowired ExecutionRepository executionRepository;

  @MockBean OkHttpClientProvider okHttpClientProvider;
  @SpyBean ExecutionCompressionProperties executionCompressionProperties;
  @MockBean ReplicationLagAwareRepository replicationLagAwareRepository;
  @MockBean Clock clock;
  @Autowired DataSourceConnectionProvider dataSourceConnectionProvider;
  @Autowired ObjectMapper mapper;
  @Autowired DefaultRegistry registry;

  private static final String TEST_APP = "myapp";
  private static final String primaryInstanceUrl = SqlTestUtil.tcJdbcUrl;
  private static final String readReplicaUrl = SqlTestUtil.tcJdbcUrl + "replica";
  private static SqlTestUtil.TestDatabase primaryInstance =
      SqlTestUtil.initDatabase(primaryInstanceUrl, SQLDialect.MYSQL);
  private static SqlTestUtil.TestDatabase readReplica =
      SqlTestUtil.initDatabase(readReplicaUrl, SQLDialect.MYSQL);
  private static final ULID ID_GENERATOR = new ULID();
  private final long defaultPoolUpdatedAt = 10000L;
  private final long readPoolUpdatedAt = 5000L;
  private final long readPoolCompressedUpdatedAt = 3000L;
  private final long firstStageReadPoolUpdatedAt = 5000L;
  private final long secondStageReadPoolUpdatedAt = 2000L;
  private final String pipelineId = ID_GENERATOR.nextULID();
  private final PipelineExecution defaultPoolPipelineExecution =
      new PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineId, TEST_APP);
  private final PipelineExecution readPoolPipelineExecution =
      new PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineId, TEST_APP);

  @DynamicPropertySource
  static void registerSQLProperties(DynamicPropertyRegistry registry) {
    registry.add("sql.connectionPools.default.jdbcUrl", () -> primaryInstanceUrl);
    registry.add("sql.migration.jdbcUrl", () -> primaryInstanceUrl);
    registry.add("sql.connectionPools.read.jdbcUrl", () -> readReplicaUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    // Set some fields in the PipelineExecutions to differentiate them from each other
    defaultPoolPipelineExecution.setName("defaultPoolPipelineExecution");
    defaultPoolPipelineExecution.setApplication("defaultPoolPipelineExecutionApp");
    readPoolPipelineExecution.setName("readPoolPipelineExecution");
    readPoolPipelineExecution.setApplication("readPoolPipelineExecutionApp");

    ExecutionStatus defaultPoolExecutionStatus = ExecutionStatus.RUNNING; // arbitrary
    ExecutionStatus readPoolExecutionStatus =
        ExecutionStatus.NOT_STARTED; // arbitrary, but different than the default pool
    assertThat(defaultPoolExecutionStatus).isNotEqualTo(readPoolExecutionStatus);
    defaultPoolPipelineExecution.setStatus(defaultPoolExecutionStatus);
    readPoolPipelineExecution.setStatus(readPoolExecutionStatus);
  }

  @AfterEach
  void resetState() throws SQLException {
    try (Connection connection = primaryInstance.dataSource.getConnection();
        Statement stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM pipelines");
      stmt.execute("DELETE FROM pipelines_compressed_executions");
      stmt.execute("DELETE FROM pipeline_stages");
      stmt.execute("DELETE FROM pipeline_stages_compressed_executions");
    }

    try (Connection connection = readReplica.dataSource.getConnection();
        Statement stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM pipelines");
      stmt.execute("DELETE FROM pipelines_compressed_executions");
      stmt.execute("DELETE FROM pipeline_stages");
      stmt.execute("DELETE FROM pipeline_stages_compressed_executions");
    }

    // Reset metrics
    registry.reset();
  }

  @AfterAll
  static void cleanup() {
    SqlTestUtil.cleanupDb(primaryInstance.context);
    SqlTestUtil.cleanupDb(readReplica.context);
  }

  @Test
  void useReadPoolWhenConsistencyIsNotRequired() throws SQLException, IOException {
    initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
    PipelineExecution execution =
        executionRepository.retrieve(
            ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.NONE);
    assertThat(execution.getName()).isEqualTo(readPoolPipelineExecution.getName());
  }

  @Test
  void retrieveWithPresentNotSupported() {
    assertThatThrownBy(
            () ->
                executionRepository.retrieve(
                    ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.PRESENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadReplicaRequirement.PRESENT not supported");
  }

  @Nested
  class TestRetrieveUncompressedExecutions {
    @BeforeEach
    void setup() {
      doReturn(0).when(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineId);
    }

    @Test
    void missingFromReplicationLagRepositoryExistsInBothPools() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);

      doReturn(null).when(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      // When there's no info in the replicationLagRepository, there's no way to
      // verify whether the pipeline from the read pool is up to date, so expect
      // the code to return the pipeline from the default pool.
      assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
      validateReadPoolMetricsOnMissingFromReplicationLagRepository();
    }

    @Test
    void getApplicationExistsInBothPools() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);

      String application = executionRepository.getApplication(pipelineId);

      assertThat(application).isEqualTo(readPoolPipelineExecution.getApplication());
      validateReadPoolMetricsOnSuccess();

      // Since getApplication uses ReadReplicaRequirement.PRESENT, being up to
      // date doesn't matter.  Expect the code to return the pipeline from the
      // read pool without using the replicationLagAwareRepository.
      verifyNoInteractions(replicationLagAwareRepository);
    }

    @Test
    void getStatusMissingFromReplicationLagRepositoryExistsInBothPools() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);

      doReturn(null).when(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineId);

      String status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      // When there's no info in the replicationLagRepository, there's no way to
      // verify whether the pipeline from the read pool is up to date, so expect
      // the code to return the pipeline from the default pool.
      assertThat(status).isEqualTo(defaultPoolPipelineExecution.getStatus().toString());
      validateReadPoolMetricsOnMissingFromReplicationLagRepository();

      // With present, it doesn't matter that there's no info in the
      // replicationLagRepository.  The info comes from the read pool.
      status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.PRESENT);
      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
    }

    @Test
    void missingFromReplicationLagRepositoryExistsInReadPoolOnly() throws Exception {
      initDBWithExecution(pipelineId, null, readPoolPipelineExecution);

      doReturn(null).when(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineId);

      assertThatThrownBy(
              () ->
                  executionRepository.retrieve(
                      ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE))
          .isInstanceOf(ExecutionNotFoundException.class);

      validateReadPoolMetricsOnMissingFromReplicationLagRepository();
    }

    @Test
    void getApplicationExistsInReadPoolOnly() throws Exception {
      initDBWithExecution(pipelineId, null, readPoolPipelineExecution);

      String application = executionRepository.getApplication(pipelineId);

      // Since getApplication uses ReadReplicaRequirement.PRESENT, being up to
      // date doesn't matter.  Expect the code to return the pipeline from the
      // read pool without using the replicationLagAwareRepository.
      assertThat(application).isEqualTo(readPoolPipelineExecution.getApplication());
      validateReadPoolMetricsOnSuccess();
      verifyNoInteractions(replicationLagAwareRepository);
    }

    @Test
    void getStatusMissingFromReplicationLagRepositoryExistsInReadPoolOnly() throws Exception {
      initDBWithExecution(pipelineId, null, readPoolPipelineExecution);

      doReturn(null).when(replicationLagAwareRepository).getPipelineExecutionUpdate(pipelineId);

      assertThatThrownBy(
              () -> executionRepository.getStatus(pipelineId, ReadReplicaRequirement.UP_TO_DATE))
          .isInstanceOf(ExecutionNotFoundException.class);

      validateReadPoolMetricsOnMissingFromReplicationLagRepository();

      // With present, it doesn't matter that there's no info in the
      // replicationLagRepository.  The info comes from the read pool.
      String status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.PRESENT);
      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
    }

    @Test
    void readPoolIsMoreRecentThanExpected() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdatedAt > expected pipeline execution update
      // i.e. the read pool is up-to-date and contains a more recent execution than what we need
      doReturn(Instant.ofEpochMilli(ThreadLocalRandom.current().nextLong(0L, readPoolUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(readPoolPipelineExecution.getName());
      validateReadPoolMetricsOnSuccess();
    }

    @Test
    void getStatusReadPoolIsMoreRecentThanExpected() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdatedAt > expected pipeline execution update
      // i.e. the read pool is up-to-date and contains a more recent execution than what we need
      doReturn(Instant.ofEpochMilli(ThreadLocalRandom.current().nextLong(0L, readPoolUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      String status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
      validateReadPoolMetricsOnSuccess();

      // Same result using present
      status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.PRESENT);
      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
    }

    @Test
    void readPoolIsUpToDate() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdateAt == expected pipeline execution update
      // i.e. the read pool is up-to-date
      doReturn(Instant.ofEpochMilli(readPoolUpdatedAt))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(readPoolPipelineExecution.getName());
      validateReadPoolMetricsOnSuccess();
    }

    @Test
    void getStatusReadPoolIsUpToDate() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdateAt == expected pipeline execution update
      // i.e. the read pool is up-to-date
      doReturn(Instant.ofEpochMilli(readPoolUpdatedAt))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      String status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
      validateReadPoolMetricsOnSuccess();

      // Same result using present
      status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.PRESENT);
      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
    }

    @Test
    void readPoolIsNotUpToDate() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdatedAt < expected pipeline execution update
      // i.e. the read pool is not up-to-date
      doReturn(
              Instant.ofEpochMilli(
                  ThreadLocalRandom.current()
                      .nextLong(readPoolUpdatedAt + 1, defaultPoolUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
      validateReadPoolMetricsOnFailure("invalid_version");
    }

    @Test
    void getStatusReadPoolIsNotUpToDate() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdatedAt < expected pipeline execution update
      // i.e. the read pool is not up-to-date
      doReturn(
              Instant.ofEpochMilli(
                  ThreadLocalRandom.current()
                      .nextLong(readPoolUpdatedAt + 1, defaultPoolUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      String status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(status).isEqualTo(defaultPoolPipelineExecution.getStatus().toString());
      validateReadPoolMetricsOnFailure("invalid_version");

      // With present, it doesn't matter that the read replica is out of date.
      status = executionRepository.getStatus(pipelineId, ReadReplicaRequirement.PRESENT);
      assertThat(status).isEqualTo(readPoolPipelineExecution.getStatus().toString());
    }

    @Test
    void executionDoesNotExist() {
      // This exercises the case where the execution exists in neither the
      // default pool nor the read pool.
      String nonexistentId = ID_GENERATOR.nextULID();
      assertThrows(
          ExecutionNotFoundException.class,
          () -> {
            executionRepository.retrieve(
                ExecutionType.PIPELINE, nonexistentId, ReadReplicaRequirement.UP_TO_DATE);
          });
      validateReadPoolMetricsOnMissingExecution(false);
    }

    @Test
    void getApplicationExecutionDoesNotExist() {
      // This exercises the case where the execution exists in neither the
      // default pool nor the read pool.
      String nonexistentId = ID_GENERATOR.nextULID();
      assertThrows(
          ExecutionNotFoundException.class,
          () -> {
            executionRepository.getApplication(pipelineId);
          });

      validateReadPoolMetricsOnMissingExecution(false);
    }

    @Test
    void getStatusExecutionDoesNotExist() {
      // This exercises the case where the execution exists in neither the
      // default pool nor the read pool.
      String nonexistentId = ID_GENERATOR.nextULID();
      assertThrows(
          ExecutionNotFoundException.class,
          () -> {
            executionRepository.getStatus(
                pipelineId, ReadReplicaRequirement.UP_TO_DATE /* arbitrary */);
          });

      validateReadPoolMetricsOnMissingExecution(false);
    }

    @Test
    void executionExistsInDefaultPoolButNotReadPool() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, null);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
      validateReadPoolMetricsOnMissingExecution(true);
    }

    @Test
    void getApplicationExecutionExistsInDefaultPoolButNotReadPool() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, null);

      String application = executionRepository.getApplication(pipelineId);

      assertThat(application).isEqualTo(defaultPoolPipelineExecution.getApplication());
      validateReadPoolMetricsOnMissingExecution(true);
    }

    @Test
    void getStatusExecutionExistsInDefaultPoolButNotReadPool() throws Exception {
      initDBWithExecution(pipelineId, defaultPoolPipelineExecution, null);

      String status =
          executionRepository.getStatus(
              pipelineId, ReadReplicaRequirement.UP_TO_DATE /* anything but none */);

      assertThat(status).isEqualTo(defaultPoolPipelineExecution.getStatus().toString());
      validateReadPoolMetricsOnMissingExecution(true);
    }
  }

  @Nested
  class TestRetrieveCompressedExecutions {
    private final String pipelineId = ID_GENERATOR.nextULID();
    private final PipelineExecution defaultPoolPipelineExecution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineId, "myapp");
    private final PipelineExecution readPoolPipelineExecution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, pipelineId, "myapp");

    @BeforeEach
    void setup() {
      doReturn(true).when(executionCompressionProperties).getEnabled();
      doReturn(0).when(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineId);
    }

    @Test
    void readPoolIsUpToDate() throws Exception {
      initDBWithCompressedExecution(
          pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdatedAt > readPoolCompressedUpdatedAt > expected pipeline execution update
      // i.e. both the pipeline and compressed pipeline execution tables are up-to-date
      doReturn(
              Instant.ofEpochMilli(
                  ThreadLocalRandom.current().nextLong(0L, readPoolCompressedUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(readPoolPipelineExecution.getName());
      validateReadPoolMetricsOnSuccess();
    }

    @Test
    void readPoolCompressedExecutionIsNotUpToDate() throws Exception {
      initDBWithCompressedExecution(
          pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // given readPoolUpdatedAt > expected pipeline execution update > readPoolCompressedUpdatedAt
      // i.e. the pipeline execution table is up-to-date but the compressed pipeline execution table
      // is not
      doReturn(
              Instant.ofEpochMilli(
                  ThreadLocalRandom.current()
                      .nextLong(readPoolCompressedUpdatedAt + 1, readPoolUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
      validateReadPoolMetricsOnFailure("invalid_version");
    }

    @Test
    void readPoolNoExecutionsAreUpToDate() throws Exception {
      initDBWithCompressedExecution(
          pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
      // when expected pipeline execution update > readPoolUpdatedAt > readPoolCompressedUpdatedAt
      // i.e. neither the pipeline nor the compressed pipeline execution tables are up-to-date
      doReturn(
              Instant.ofEpochMilli(
                  ThreadLocalRandom.current()
                      .nextLong(readPoolUpdatedAt + 1, defaultPoolUpdatedAt)))
          .when(replicationLagAwareRepository)
          .getPipelineExecutionUpdate(pipelineId);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
      validateReadPoolMetricsOnFailure("invalid_version");
    }

    @Test
    void executionDoesNotExist() {
      // This exercises the case where the execution exists in neither the
      // default pool nor the read pool.
      String nonexistentId = ID_GENERATOR.nextULID();
      assertThrows(
          ExecutionNotFoundException.class,
          () -> {
            executionRepository.retrieve(
                ExecutionType.PIPELINE, nonexistentId, ReadReplicaRequirement.UP_TO_DATE);
          });
      validateReadPoolMetricsOnMissingExecution(false);
    }

    @Test
    void executionExistsInDefaultPoolButNotReadPool() throws Exception {
      initDBWithCompressedExecution(pipelineId, defaultPoolPipelineExecution, null);

      PipelineExecution execution =
          executionRepository.retrieve(
              ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

      assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
      validateReadPoolMetricsOnMissingExecution(true);
    }

    @Nested
    class TestRetrieveCompressedExecutionsWithStages {
      private final StageExecution firstStageDefaultPool =
          new StageExecutionImpl(
              defaultPoolPipelineExecution, "test", "stage 1 default", new HashMap<>());
      private final StageExecution secondStageDefaultPool =
          new StageExecutionImpl(
              defaultPoolPipelineExecution, "test", "stage 2 default", new HashMap<>());
      private final StageExecution firstStageReadPool =
          new StageExecutionImpl(
              readPoolPipelineExecution, "test", "stage 1 read", new HashMap<>());
      private final StageExecution secondStageReadPool =
          new StageExecutionImpl(
              readPoolPipelineExecution, "test", "stage 2 read", new HashMap<>());

      @BeforeEach
      void setUpTables() throws SQLException, IOException {
        initDBWithCompressedExecution(
            pipelineId, defaultPoolPipelineExecution, readPoolPipelineExecution);
        initDBWithStages(
            pipelineId,
            Map.of(
                firstStageDefaultPool,
                defaultPoolUpdatedAt,
                secondStageDefaultPool,
                defaultPoolUpdatedAt),
            Map.of(
                firstStageReadPool,
                firstStageReadPoolUpdatedAt,
                secondStageReadPool,
                secondStageReadPoolUpdatedAt));
        doReturn(2).when(replicationLagAwareRepository).getPipelineExecutionNumStages(pipelineId);
      }

      @Test
      void readPoolIsUpToDate() {
        // given readPoolUpdatedAt > readPoolCompressedUpdatedAt > expected pipeline execution
        // update and
        // firstStageReadPoolUpdatedAt > secondStageReadPoolUpdatedAt > expected stage execution
        // updates
        // i.e. all pipeline and stage execution tables are up-to-date
        doReturn(
                Instant.ofEpochMilli(
                    ThreadLocalRandom.current().nextLong(0L, readPoolCompressedUpdatedAt)))
            .when(replicationLagAwareRepository)
            .getPipelineExecutionUpdate(pipelineId);
        doReturn(
                Instant.ofEpochMilli(
                    ThreadLocalRandom.current().nextLong(0L, firstStageReadPoolUpdatedAt)))
            .when(replicationLagAwareRepository)
            .getStageExecutionUpdate(firstStageReadPool.getId());
        doReturn(
                Instant.ofEpochMilli(
                    ThreadLocalRandom.current().nextLong(0L, secondStageReadPoolUpdatedAt)))
            .when(replicationLagAwareRepository)
            .getStageExecutionUpdate(secondStageReadPool.getId());

        PipelineExecution execution =
            executionRepository.retrieve(
                ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

        assertThat(execution.getName()).isEqualTo(readPoolPipelineExecution.getName());
        List<String> expectedStageNames = new ArrayList<>();
        expectedStageNames.add(firstStageReadPool.getName());
        expectedStageNames.add(secondStageReadPool.getName());
        Collections.sort(expectedStageNames);
        List<String> actualStageNames =
            execution.getStages().stream()
                .map(StageExecution::getName)
                .sorted()
                .collect(Collectors.toList());
        assertThat(actualStageNames).isEqualTo(expectedStageNames);
        validateReadPoolMetricsOnSuccess();
      }

      @Test
      void readPoolStageExecutionIsNotUpToDate() {
        // given readPoolUpdatedAt > readPoolCompressedUpdatedAt > expected pipeline execution
        // update
        // and firstStageReadPoolUpdatedAt > expected first stage execution update
        // and expected second stage execution update > secondStageReadPoolUpdatedAt
        // i.e. all executions in the read replica are up-to-date except for secondStageReadPool
        doReturn(
                Instant.ofEpochMilli(
                    ThreadLocalRandom.current().nextLong(0L, readPoolCompressedUpdatedAt)))
            .when(replicationLagAwareRepository)
            .getPipelineExecutionUpdate(pipelineId);
        doReturn(
                Instant.ofEpochMilli(
                    ThreadLocalRandom.current().nextLong(0L, firstStageReadPoolUpdatedAt)))
            .when(replicationLagAwareRepository)
            .getStageExecutionUpdate(firstStageReadPool.getId());
        doReturn(
                Instant.ofEpochMilli(
                    ThreadLocalRandom.current()
                        .nextLong(secondStageReadPoolUpdatedAt + 1, defaultPoolUpdatedAt)))
            .when(replicationLagAwareRepository)
            .getStageExecutionUpdate(secondStageReadPool.getId());

        PipelineExecution execution =
            executionRepository.retrieve(
                ExecutionType.PIPELINE, pipelineId, ReadReplicaRequirement.UP_TO_DATE);

        assertThat(execution.getName()).isEqualTo(defaultPoolPipelineExecution.getName());
        List<String> expectedStageNames = new ArrayList<>();
        expectedStageNames.add(firstStageDefaultPool.getName());
        expectedStageNames.add(secondStageDefaultPool.getName());
        Collections.sort(expectedStageNames);
        List<String> actualStageNames =
            execution.getStages().stream()
                .map(StageExecution::getName)
                .sorted()
                .collect(Collectors.toList());
        assertThat(actualStageNames).isEqualTo(expectedStageNames);
        validateReadPoolMetricsOnFailure("invalid_version");
      }
    }
  }

  // Initialize the DB with a pipeline execution
  void initDBWithExecution(
      String pipelineId,
      PipelineExecution defaultPoolPipelineExecution,
      PipelineExecution readPoolPipelineExecution)
      throws SQLException, IOException {
    String insertSql =
        "INSERT INTO pipelines (id, application, status, build_time, canceled, updated_at, body) VALUES (?, ?, ?, ?, ?, ?, ?)";
    if (defaultPoolPipelineExecution != null) {
      // populate primary instance
      try (Connection connection = primaryInstance.dataSource.getConnection();
          PreparedStatement stmt = connection.prepareStatement(insertSql)) {
        String defaultPoolBody = mapper.writeValueAsString(defaultPoolPipelineExecution);
        stmt.setString(1, pipelineId);
        stmt.setString(2, defaultPoolPipelineExecution.getApplication());
        stmt.setString(3, defaultPoolPipelineExecution.getStatus().toString());
        stmt.setLong(4, Instant.now().toEpochMilli());
        stmt.setBoolean(5, defaultPoolPipelineExecution.isCanceled());
        stmt.setLong(6, defaultPoolUpdatedAt);
        stmt.setString(7, defaultPoolBody);
        stmt.executeUpdate();
      }
    }

    // populate read replica
    if (readPoolPipelineExecution != null) {
      try (Connection connection = readReplica.dataSource.getConnection();
          PreparedStatement stmt = connection.prepareStatement(insertSql)) {
        String readPoolBody = mapper.writeValueAsString(readPoolPipelineExecution);
        stmt.setString(1, pipelineId);
        stmt.setString(2, readPoolPipelineExecution.getApplication());
        stmt.setString(3, readPoolPipelineExecution.getStatus().toString());
        stmt.setLong(4, Instant.now().toEpochMilli());
        stmt.setBoolean(5, readPoolPipelineExecution.isCanceled());
        stmt.setLong(6, readPoolUpdatedAt);
        stmt.setString(7, readPoolBody);
        stmt.executeUpdate();
      }
    }
  }

  // Initialize the DB with a pipeline execution and its compressed counterpart
  void initDBWithCompressedExecution(
      String pipelineId,
      PipelineExecution defaultPoolPipelineExecution,
      PipelineExecution readPoolPipelineExecution)
      throws SQLException, IOException {
    String insertExecutionSql =
        "INSERT INTO pipelines (id, application, status, build_time, canceled, updated_at, body) VALUES (?, ?, ?, ?, ?, ?, ?)";
    String insertCompressedExecutionSql =
        "INSERT INTO pipelines_compressed_executions (id, compressed_body, compression_type, updated_at) VALUES (?, ?, ?, ?)";
    // populate primary instance
    try (Connection connection = primaryInstance.dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(insertExecutionSql);
        PreparedStatement compressedStmt =
            connection.prepareStatement(insertCompressedExecutionSql)) {
      // write regular execution
      stmt.setString(1, pipelineId);
      stmt.setString(2, defaultPoolPipelineExecution.getApplication());
      stmt.setString(3, defaultPoolPipelineExecution.getStatus().toString());
      stmt.setLong(4, Instant.now().toEpochMilli());
      stmt.setBoolean(5, defaultPoolPipelineExecution.isCanceled());
      stmt.setLong(6, defaultPoolUpdatedAt);
      stmt.setString(7, "");
      stmt.executeUpdate();

      // write compressed execution
      String body = mapper.writeValueAsString(defaultPoolPipelineExecution);
      ByteArrayOutputStream compressedBodyStream = new ByteArrayOutputStream();
      DeflaterOutputStream deflaterOutputStream =
          new DeflaterOutputStream(compressedBodyStream, new Deflater());
      deflaterOutputStream.write(body.getBytes(StandardCharsets.UTF_8));
      deflaterOutputStream.close();
      compressedStmt.setString(1, pipelineId);
      compressedStmt.setBytes(2, compressedBodyStream.toByteArray());
      compressedStmt.setString(3, CompressionType.ZLIB.toString());
      compressedStmt.setLong(4, defaultPoolUpdatedAt);
      compressedStmt.executeUpdate();
    }

    if (readPoolPipelineExecution != null) {
      // populate read replica
      try (Connection connection = readReplica.dataSource.getConnection();
          PreparedStatement stmt = connection.prepareStatement(insertExecutionSql);
          PreparedStatement compressedStmt =
              connection.prepareStatement(insertCompressedExecutionSql)) {
        // write regular execution
        stmt.setString(1, pipelineId);
        stmt.setString(2, readPoolPipelineExecution.getApplication());
        stmt.setString(3, readPoolPipelineExecution.getStatus().toString());
        stmt.setLong(4, Instant.now().toEpochMilli());
        stmt.setBoolean(5, readPoolPipelineExecution.isCanceled());
        stmt.setLong(6, readPoolUpdatedAt);
        stmt.setString(7, "");
        stmt.executeUpdate();

        // write compressed execution
        String body = mapper.writeValueAsString(readPoolPipelineExecution);
        ByteArrayOutputStream compressedBodyStream = new ByteArrayOutputStream();
        DeflaterOutputStream deflaterOutputStream =
            new DeflaterOutputStream(compressedBodyStream, new Deflater());
        deflaterOutputStream.write(body.getBytes(StandardCharsets.UTF_8));
        deflaterOutputStream.close();
        compressedStmt.setString(1, pipelineId);
        compressedStmt.setBytes(2, compressedBodyStream.toByteArray());
        compressedStmt.setString(3, CompressionType.ZLIB.toString());
        compressedStmt.setLong(4, readPoolCompressedUpdatedAt);
        compressedStmt.executeUpdate();
      }
    }
  }

  // Initialize the DB with a list of stage executions and their compressed counterparts
  void initDBWithStages(
      String pipelineExecutionId,
      Map<StageExecution, Long> defaultPoolStageExecutionUpdates,
      Map<StageExecution, Long> readPoolStageExecutionUpdates)
      throws SQLException, IOException {
    String insertExecutionSql =
        "INSERT INTO pipeline_stages (id, execution_id, status, updated_at, body) VALUES (?, ?, ?, ?, ?)";
    String insertCompressedExecutionSql =
        "INSERT INTO pipeline_stages_compressed_executions (id, compressed_body, compression_type, updated_at) VALUES (?, ?, ?, ?)";
    // populate primary instance
    try (Connection connection = primaryInstance.dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(insertExecutionSql);
        PreparedStatement compressedStmt =
            connection.prepareStatement(insertCompressedExecutionSql)) {
      for (Map.Entry<StageExecution, Long> entry : defaultPoolStageExecutionUpdates.entrySet()) {
        StageExecution stageExecution = entry.getKey();
        Long updatedAt = entry.getValue();
        // write regular execution
        stmt.setString(1, stageExecution.getId());
        stmt.setString(2, pipelineExecutionId);
        stmt.setString(3, stageExecution.getStatus().toString());
        stmt.setLong(4, updatedAt);
        stmt.setString(5, "");
        stmt.addBatch();

        // write compressed execution
        String body = mapper.writeValueAsString(stageExecution);
        ByteArrayOutputStream compressedBodyStream = new ByteArrayOutputStream();
        DeflaterOutputStream deflaterOutputStream =
            new DeflaterOutputStream(compressedBodyStream, new Deflater());
        deflaterOutputStream.write(body.getBytes(StandardCharsets.UTF_8));
        deflaterOutputStream.close();
        compressedStmt.setString(1, stageExecution.getId());
        compressedStmt.setBytes(2, compressedBodyStream.toByteArray());
        compressedStmt.setString(3, CompressionType.ZLIB.toString());
        compressedStmt.setLong(4, updatedAt);
        compressedStmt.addBatch();
      }
      stmt.executeBatch();
      compressedStmt.executeBatch();
    }

    // populate read replica
    try (Connection connection = readReplica.dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(insertExecutionSql);
        PreparedStatement compressedStmt =
            connection.prepareStatement(insertCompressedExecutionSql)) {
      for (Map.Entry<StageExecution, Long> entry : readPoolStageExecutionUpdates.entrySet()) {
        StageExecution stageExecution = entry.getKey();
        Long updatedAt = entry.getValue();
        // write regular execution
        stmt.setString(1, stageExecution.getId());
        stmt.setString(2, pipelineExecutionId);
        stmt.setString(3, stageExecution.getStatus().toString());
        stmt.setLong(4, updatedAt);
        stmt.setString(5, "");
        stmt.addBatch();

        // write compressed execution
        String body = mapper.writeValueAsString(stageExecution);
        ByteArrayOutputStream compressedBodyStream = new ByteArrayOutputStream();
        DeflaterOutputStream deflaterOutputStream =
            new DeflaterOutputStream(compressedBodyStream, new Deflater());
        deflaterOutputStream.write(body.getBytes(StandardCharsets.UTF_8));
        deflaterOutputStream.close();
        compressedStmt.setString(1, stageExecution.getId());
        compressedStmt.setBytes(2, compressedBodyStream.toByteArray());
        compressedStmt.setString(3, CompressionType.ZLIB.toString());
        compressedStmt.setLong(4, updatedAt);
        compressedStmt.addBatch();
      }
      stmt.executeBatch();
      compressedStmt.executeBatch();
    }
  }

  void validateReadPoolMetricsOnSuccess() {
    assertThat(
            registry
                .counter("executionRepository.sql.readPool.retrieveSucceeded", "numAttempts", "1")
                .count())
        .isEqualTo(1);
    List<String> resultCodes =
        List.of(
            "success",
            "not_found",
            "invalid_version",
            "missing_from_replication_lag_repository",
            "failure");
    resultCodes.forEach(
        resultCode -> {
          assertThat(
                  registry
                      .counter(
                          "executionRepository.sql.readPool.retrieveFailed",
                          "result_code",
                          resultCode)
                      .count())
              .isEqualTo(0);
        });
    assertThat(registry.counter("executionRepository.sql.readPool.retrieveTotalAttempts").count())
        .isEqualTo(1);
  }

  /** Use a plain String for resultCode so it's easier to see behavior / metric tag changes. */
  void validateReadPoolMetricsOnFailure(String resultCode) {
    assertThat(
            registry
                .counter("executionRepository.sql.readPool.retrieveSucceeded", "numAttempts", "1")
                .count())
        .isEqualTo(0);
    assertThat(
            registry
                .counter(
                    "executionRepository.sql.readPool.retrieveFailed", "result_code", resultCode)
                .count())
        .isEqualTo(1);
    assertThat(registry.counter("executionRepository.sql.readPool.retrieveTotalAttempts").count())
        .isEqualTo(1);
  }

  void validateReadPoolMetricsOnMissingExecution(boolean expectNotFoundMetric) {
    assertThat(
            registry
                .counter("executionRepository.sql.readPool.retrieveSucceeded", "numAttempts", "1")
                .count())
        .isEqualTo(0);
    assertThat(
            registry
                .counter(
                    "executionRepository.sql.readPool.retrieveFailed", "result_code", "not_found")
                .count())
        .isEqualTo(expectNotFoundMetric ? 1 : 0);
    assertThat(registry.counter("executionRepository.sql.readPool.retrieveTotalAttempts").count())
        .isEqualTo(1);
  }

  void validateReadPoolMetricsOnMissingFromReplicationLagRepository() {
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
                    "missing_from_replication_lag_repository")
                .count())
        .isEqualTo(1);
    assertThat(registry.counter("executionRepository.sql.readPool.retrieveTotalAttempts").count())
        .isEqualTo(1);
  }

  @TestConfiguration
  static class SqlExecutionRepositoryReadReplicaTestConfiguration {
    @Bean
    ObjectMapper orcaObjectMapper() {
      return OrcaObjectMapper.getInstance();
    }

    @Bean
    DefaultRegistry resettableRegistry() {
      return new DefaultRegistry();
    }
  }
}

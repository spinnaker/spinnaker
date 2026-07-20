/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.pubsub;

import static com.netflix.spinnaker.cats.pubsub.StateMachine.State.DELETED;
import static com.netflix.spinnaker.cats.pubsub.StateMachine.State.FAILED;
import static com.netflix.spinnaker.cats.pubsub.StateMachine.State.FINISHED;
import static com.netflix.spinnaker.cats.pubsub.StateMachine.State.NOT_STARTED;
import static com.netflix.spinnaker.cats.pubsub.StateMachine.State.PENDING;
import static com.netflix.spinnaker.cats.pubsub.StateMachine.State.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StateMachineTest {

  private StateMachine stateMachine;
  private DSLContext dslContext;
  private Connection connection;

  private final Agent mockAgent =
      new CachingAgent() {

        @Override
        public String getAgentType() {
          return "test-agent";
        }

        @Override
        public String getProviderName() {
          return "NONE";
        }

        @Override
        public Collection<AgentDataType> getProvidedDataTypes() {
          return List.of();
        }

        @Override
        public CacheResult loadData(ProviderCache providerCache) {
          return Map::of;
        }
      };

  @BeforeEach
  void setUp() throws SQLException {
    // Set up H2 in-memory database
    connection =
        DriverManager.getConnection(
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
    dslContext = DSL.using(connection, SQLDialect.MYSQL);

    // Create the pubsub_agent_state table.  This DDL MUST stay aligned with the liquibase
    // migration (clouddriver-sql db.changelog 20250908-pubsub-scheduler.yml): notably there are NO
    // column defaults there, so nullable columns must be handled by the StateMachine itself.
    dslContext.execute(
        """
          CREATE TABLE IF NOT EXISTS pubsub_agent_state (
              agent_type VARCHAR(512) PRIMARY KEY,
              current_state VARCHAR(20) NOT NULL,
              last_transition_time BIGINT,
              last_execution_time BIGINT,
              last_duration BIGINT,
              data_processed BIGINT
          )
      """);

    // Set up StateMachine with real DSLContext
    stateMachine = new StateMachine(dslContext);
  }

  @AfterEach
  void tearDown() throws SQLException {
    // Clean up database
    dslContext.execute("DROP TABLE IF EXISTS pubsub_agent_state");
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  @Test
  void getAgent_shouldReturnAgentStateWhenAgentExists() {
    // Given
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), RUNNING);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(RUNNING.name());
  }

  @Test
  void getAgent_shouldReturnNullWhenAgentDoesNotExist() {
    // Given
    String agentType = "non-existent-agent";
    StateMachine.AgentState result = stateMachine.getAgent(agentType);
    assertNull(result);
  }

  @Test
  void acquireLock_shouldSuccessfullyAcquireLockAndUpdateState() throws Exception {
    // Given
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), NOT_STARTED);
    // When thread A locks an agent...
    stateMachine.changeStateUnlessMarkedForDeletion(mockAgent.getAgentType(), RUNNING);
    // Then the second lock should fail with an
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(RUNNING.name());
  }

  @Test
  void agentShouldNotMoveOutOfDeletedStatusOnStateChange() throws Exception {
    // Create new agent with running state.
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), DELETED);
    stateMachine.changeStateUnlessMarkedForDeletion(mockAgent.getAgentType(), RUNNING);
    // Then the second lock should fail with an
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(DELETED.name());
  }

  @Test
  void disable_shouldMarkAgentAsDeleted() {
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), NOT_STARTED);
    stateMachine.disable(mockAgent);

    // Then
    StateMachine.AgentState updatedAgent = stateMachine.getAgent(mockAgent.getAgentType());
    assertNotNull(updatedAgent);
    assertThat(updatedAgent.getCurrentState()).isEqualTo(DELETED.name());
  }

  @Test
  void actuallyDeleteAnAgent() {
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), NOT_STARTED);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType())).isNotNull();
    stateMachine.delete(mockAgent.getAgentType());
    assertThat(stateMachine.getAgent(mockAgent.getAgentType())).isNull();
  }

  @Test
  void delete_shouldHandleNonExistentAgent() {
    String agentType = "non-existent-agent";
    stateMachine.delete(agentType); // this should be a "no-op"
    assertNull(stateMachine.getAgent(agentType));
  }

  @Test
  void createOrUpdateAgent_shouldUpdateExistingAgentWhenNotInIgnoredStates() {
    // Given
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), PENDING);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(PENDING.name());
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), FAILED);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(PENDING.name());
    stateMachine.changeStateUnlessMarkedForDeletion(mockAgent.getAgentType(), FAILED);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(FAILED.name());
  }

  @Test
  void createOrUpdateAgent_shouldInsertNewAgentWhenNotExists() {
    // When
    assertThat(stateMachine.getAgent("new-agent")).isNull();
    stateMachine.createOrUpdateAgent("new-agent", PENDING);

    // Then
    StateMachine.AgentState newAgent = stateMachine.getAgent("new-agent");
    assertNotNull(newAgent);
    assertEquals("new-agent", newAgent.getAgentType());
    assertThat(newAgent.getCurrentState()).isEqualTo(PENDING.name());
    assertEquals(Integer.MAX_VALUE, newAgent.getLastDuration());
    assertTrue(newAgent.getLastTransitionTime() > 0);
    assertEquals(0, newAgent.getLastExecutionTime());
  }

  @Test
  void listAgentsFilteredWhereIn_shouldReturnFilteredAgents() {
    // Given
    stateMachine.createOrUpdateAgent("new-agent1", PENDING);
    stateMachine.createOrUpdateAgent("new-agent2", FINISHED);
    stateMachine.createOrUpdateAgent("new-agent3", RUNNING);

    // When
    List<StateMachine.AgentState> result =
        stateMachine.listAgentsFilteredWhereIn(Set.of(PENDING, StateMachine.State.RUNNING));

    // Then
    assertEquals(2, result.size());
    assertTrue(
        result.stream()
            .anyMatch(
                agent ->
                    "new-agent1".equals(agent.getAgentType())
                        && agent.getCurrentState().equals(PENDING.name())));
    assertTrue(
        result.stream()
            .anyMatch(
                agent ->
                    "new-agent3".equals(agent.getAgentType())
                        && agent.getCurrentState().equals(RUNNING.name())));
    assertFalse(result.stream().anyMatch(agent -> "new-agent2".equals(agent.getAgentType())));
  }

  @Test
  void listAgentsFilteredWhereIn_shouldReturnAllAgentsWhenFilterIsNull() {
    stateMachine.createOrUpdateAgent("new-agent1", PENDING);
    stateMachine.createOrUpdateAgent("new-agent2", FINISHED);
    stateMachine.createOrUpdateAgent("new-agent3", RUNNING);
    Set<StateMachine.State> filterList = Set.of(PENDING, StateMachine.State.RUNNING);
    List<StateMachine.AgentState> result = stateMachine.listAgentsFilteredWhereIn(filterList);
    assertEquals(2, result.size());
    result = stateMachine.listAgentsFilteredWhereIn(null);
    assertEquals(3, result.size());
  }

  @Test
  void listAgentsFilteredWhereIn_shouldReturnEmptyListWhenNoAgents() {
    // Given
    stateMachine.createOrUpdateAgent("new-agent", PENDING);
    List<StateMachine.AgentState> result = stateMachine.listAgentsFilteredWhereIn(Set.of(RUNNING));
    assertTrue(result.isEmpty());
  }

  @Test
  void tryTransition_shouldOnlyTransitionFromExpectedStates() {
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), FINISHED);

    // Simulates two scheduler/runner replicas racing: the first conditional update wins...
    assertEquals(
        1, stateMachine.tryTransition(mockAgent.getAgentType(), Set.of(FINISHED), PENDING));
    // ...and the second caller loses because the agent is no longer in an expected state.
    assertEquals(
        0, stateMachine.tryTransition(mockAgent.getAgentType(), Set.of(FINISHED), PENDING));
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(PENDING.name());
  }

  @Test
  void tryTransition_shouldNotResurrectDeletedAgents() {
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), DELETED);
    assertEquals(0, stateMachine.tryTransition(mockAgent.getAgentType(), Set.of(PENDING), RUNNING));
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(DELETED.name());
  }

  @Test
  void tryTransition_samStateTransitionShouldBumpTransitionTime() throws Exception {
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), PENDING);
    long initialTransitionTime =
        stateMachine.getAgent(mockAgent.getAgentType()).getLastTransitionTime();
    Thread.sleep(5);
    // The stale-PENDING requeue relies on PENDING->PENDING advancing last_transition_time so an
    // agent is only re-published once per requeue window.
    assertEquals(1, stateMachine.tryTransition(mockAgent.getAgentType(), Set.of(PENDING), PENDING));
    assertTrue(
        stateMachine.getAgent(mockAgent.getAgentType()).getLastTransitionTime()
            > initialTransitionTime);
  }

  @Test
  void getAgent_shouldTolerateRowsWithNullColumns() {
    // Rows written before data_processed existed (or by hand) can hold NULLs - the migration
    // declares no column defaults.
    dslContext.execute(
        "INSERT INTO pubsub_agent_state (agent_type, current_state) VALUES ('legacy-agent', 'FINISHED')");

    StateMachine.AgentState legacy = stateMachine.getAgent("legacy-agent");
    assertNotNull(legacy);
    assertEquals(0L, legacy.getDataProcessed());
    assertEquals(0L, legacy.getLastDuration());
    assertEquals(0L, legacy.getLastExecutionTime());
    assertEquals(0L, legacy.getLastTransitionTime());
  }

  @Test
  void statesToIgnoreOnExisting_shouldContainCorrectStates() {
    // DELETED is deliberately NOT ignored: a schedule() call must resurrect an agent that a
    // previous unschedule() marked DELETED (providers reschedule via unschedule/schedule pairs).
    assertEquals(3, StateMachine.STATES_TO_IGNORE_ON_EXISTING.size());
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(PENDING));
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(FINISHED));
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(RUNNING));
  }

  @Test
  void createOrUpdateAgent_shouldResurrectDeletedAgents() {
    // Simulates a provider rescheduling an agent: unschedule marks it DELETED, then schedule
    // (createOrUpdateAgent) must bring it back rather than leaving it dead until purged.
    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), NOT_STARTED);
    stateMachine.disable(mockAgent);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(DELETED.name());

    stateMachine.createOrUpdateAgent(mockAgent.getAgentType(), NOT_STARTED);
    assertThat(stateMachine.getAgent(mockAgent.getAgentType()).getCurrentState())
        .isEqualTo(NOT_STARTED.name());
  }

  @Test
  void countAgentsByState_shouldGroupCorrectly() {
    stateMachine.createOrUpdateAgent("agent-1", PENDING);
    stateMachine.createOrUpdateAgent("agent-2", PENDING);
    stateMachine.createOrUpdateAgent("agent-3", FINISHED);

    Map<String, Long> counts = stateMachine.countAgentsByState();
    assertEquals(2L, counts.get(PENDING.name()));
    assertEquals(1L, counts.get(FINISHED.name()));
    assertNull(counts.get(RUNNING.name()));
  }

  @Test
  void integrationTest_completeAgentLifecycle() {
    // Given
    String agentType = "lifecycle-agent";

    // When & Then - Create new agent
    stateMachine.createOrUpdateAgent(agentType, PENDING);
    StateMachine.AgentState agent = stateMachine.getAgent(agentType);
    assertNotNull(agent);
    assertThat(agent.getCurrentState()).isEqualTo(PENDING.name());

    // When & Then - Change state to running
    assertThat(stateMachine.changeStateUnlessMarkedForDeletion(agentType, RUNNING)).isEqualTo(1);
    assertThat(stateMachine.getAgent(agentType).getCurrentState()).isEqualTo(RUNNING.name());

    // When & Then - Mark as completed
    assertEquals(1, stateMachine.markAgentCompleted(agentType, 3000L, 50));
    agent = stateMachine.getAgent(agentType);
    assertThat(agent.getCurrentState()).isEqualTo(FINISHED.name());
    assertEquals(3000L, agent.getLastDuration());
    assertEquals(50L, agent.getDataProcessed());
  }
}

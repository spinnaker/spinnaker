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
import com.netflix.spinnaker.clouddriver.config.PubSubSchedulerProperties;
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

  private PubSubSchedulerProperties mockProperties = new PubSubSchedulerProperties();
  private Agent mockAgent =
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

    // Create the pubsub_agent_state table
    dslContext.execute(
        """
          CREATE TABLE IF NOT EXISTS pubsub_agent_state (
              agent_type VARCHAR(255) PRIMARY KEY,
              current_state VARCHAR(50) NOT NULL,
              last_execution_time BIGINT DEFAULT 0,
              last_transition_time BIGINT DEFAULT 0,
              last_duration BIGINT DEFAULT 0,
              data_processed BIGINT DEFAULT 0
          )
      """);

    // Set up StateMachine with real DSLContext
    stateMachine = new StateMachine();
    stateMachine.setJooq(dslContext);
    stateMachine.setProperties(mockProperties);
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
  void statesToIgnoreOnExisting_shouldContainCorrectStates() {
    // Then
    assertEquals(4, StateMachine.STATES_TO_IGNORE_ON_EXISTING.size());
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(DELETED));
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(PENDING));
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(FINISHED));
    assertTrue(StateMachine.STATES_TO_IGNORE_ON_EXISTING.contains(RUNNING));
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

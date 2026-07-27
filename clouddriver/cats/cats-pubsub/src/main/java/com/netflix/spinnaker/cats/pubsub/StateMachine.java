package com.netflix.spinnaker.cats.pubsub;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.table;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("cats.pubsub.enabled")
@Log4j2
@Setter
@RequiredArgsConstructor
@Alpha
public class StateMachine {
  public static final String PUBSUB_AGENT_STATE = "pubsub_agent_state";
  public static final String AGENT_TYPE = "agent_type";

  private final DSLContext jooq;

  private static long longOrZero(Long value) {
    return value == null ? 0L : value;
  }

  private static final RecordMapper<Record, AgentState> agentStateMapper =
      row ->
          AgentState.builder()
              .agentType(row.get(AGENT_TYPE, String.class))
              .currentState(row.get("current_state", String.class))
              .lastTransitionTime(longOrZero(row.get("last_transition_time", Long.class)))
              .lastExecutionTime(longOrZero(row.get("last_execution_time", Long.class)))
              .lastDuration(longOrZero(row.get("last_duration", Long.class)))
              .dataProcessed(longOrZero(row.get("data_processed", Long.class)))
              .build();

  public AgentState getAgent(String agentType) {
    return jooq
        .selectFrom(table(PUBSUB_AGENT_STATE))
        .where(field(AGENT_TYPE).eq(agentType))
        .fetch()
        .map(agentStateMapper)
        .stream()
        .findFirst()
        .orElse(null);
  }

  /**
   * Atomically transition an agent to a new state ONLY if it is currently in one of the expected
   * states. This is the coordination primitive for the scheduler/runners: because the row update is
   * conditional, exactly ONE caller wins when several schedulers or runners race on the same agent
   * (e.g. multiple replicas all receive the same pub/sub message).
   *
   * @return the number of rows updated: 1 when this caller won the transition, 0 when the agent was
   *     not in an expected state (someone else transitioned it first, it was deleted, or it no
   *     longer exists)
   */
  public int tryTransition(String agentType, Set<State> expectedCurrentStates, State stateToSet) {
    return jooq.update(table(PUBSUB_AGENT_STATE))
        .set(field("current_state"), stateToSet.name())
        .set(field("last_transition_time"), System.currentTimeMillis())
        .where(field(AGENT_TYPE).eq(agentType))
        .and(field("current_state").in(expectedCurrentStates.stream().map(State::name).toList()))
        .execute();
  }

  /**
   * Allow updating the state of a specific agent AS LONG AS It's not marked for deletion.
   *
   * @return the number of rows updated
   */
  public int changeStateUnlessMarkedForDeletion(String agentType, State stateToSet) {
    return jooq.update(table(PUBSUB_AGENT_STATE))
        .set(field("current_state"), stateToSet.name())
        .set(field("last_transition_time"), System.currentTimeMillis())
        .where(field(AGENT_TYPE).eq(agentType))
        .andNot(field("current_state").eq(StateMachine.State.DELETED.name()))
        .execute();
  }

  public int markAgentCompleted(String agentType, long executionDuration, int numberOfResults) {
    return jooq.update(table(PUBSUB_AGENT_STATE))
        .set(field("current_state"), State.FINISHED.name())
        .set(field("last_duration"), executionDuration)
        .set(field("last_execution_time"), System.currentTimeMillis())
        .set(field("last_transition_time"), System.currentTimeMillis())
        .set(field("data_processed"), numberOfResults)
        .where(field(AGENT_TYPE).eq(agentType))
        .andNot(field("current_state").eq(StateMachine.State.DELETED.name()))
        .execute();
  }

  public void disable(Agent agent) {
    // we keep the agent IN the table marked as "DELETED" so that we can on agent COMPLETION (if
    // already running) know that it's marked for deletion, and skip writing data, just let it
    // complete
    // ONLY mark a row to be deleted if it wasn't recently set to be deleted.  This handles repeated
    // "remove" calls.
    int rowsDeleted =
        jooq.update(table(PUBSUB_AGENT_STATE))
            .set(field("current_state"), StateMachine.State.DELETED.name())
            .set(field("last_transition_time"), System.currentTimeMillis())
            .where(field(AGENT_TYPE).eq(agent.getAgentType()))
            .andNot(field("current_state").eq(StateMachine.State.DELETED.name()))
            .execute();
    if (rowsDeleted != 1) {
      log.warn(
          "Failed to mark agent {} from pubsub_agent_state as deleted.  This COULD be tied to it being in execution OR something else purged it... ",
          agent.getAgentType());
    }
  }

  /**
   * Delete the actual agent from the tracking table. This should ONLY occur AFTER a few hours as
   * some agents are "rescheduled" aka deleted & recreated via an unschedule/reschedule operation.
   *
   * @param agentType
   */
  public void delete(String agentType) {
    // we keep the agent IN the table marked as "DELETED" so that we can on agent COMPLETION (if
    // already running) know that it's marked for deletion, and skip writing data, just let it
    // complete
    int rowsDeleted =
        jooq.deleteFrom(table(PUBSUB_AGENT_STATE)).where(field(AGENT_TYPE).eq(agentType)).execute();
    if (rowsDeleted != 1) {
      log.error(
          "Failed to actually delete agent {} from pubsub_agent_state as deleted.  YOU MAY Have dangling agent data as a result in the pubsub_agent_state table.",
          agentType);
    }
  }

  // States owned by the scheduler/runner lifecycle that a (re)schedule call must not clobber.
  // DELETED is deliberately NOT in this set: several providers reschedule agents via an
  // unschedule/schedule pair, and the schedule call is an explicit statement that the agent should
  // exist again - it must resurrect a row that unschedule marked DELETED, otherwise the agent
  // stays dead until the purge removes the row and nothing ever re-creates it.
  public static final Set<State> STATES_TO_IGNORE_ON_EXISTING =
      Set.of(State.PENDING, State.FINISHED, State.RUNNING);

  /**
   * Register an agent, or update the state of an existing agent that is "at rest" (i.e. not
   * PENDING/RUNNING/FINISHED - states owned by the scheduler/runner lifecycle). Used when providers
   * schedule (or reschedule via unschedule/reschedule) agents; a DELETED agent is resurrected.
   */
  public void createOrUpdateAgent(String agentType, State stateToSet) {
    try {
      int inserted =
          jooq.insertInto(table(PUBSUB_AGENT_STATE))
              .set(field(AGENT_TYPE), agentType)
              .set(field("current_state"), stateToSet.name())
              .set(field("last_transition_time"), System.currentTimeMillis())
              .set(field("last_execution_time"), 0L)
              // Sentinel meaning "never completed a run" - real durations replace it on the first
              // successful completion.
              .set(field("last_duration"), (long) Integer.MAX_VALUE)
              .set(field("data_processed"), 0L)
              .onDuplicateKeyIgnore()
              .execute();
      if (inserted == 0) {
        // already exists... so we probably are rescheduling an agent.  Some of the providers do an
        // unschedule/reschedule to reschedule various agents.
        jooq.update(table(PUBSUB_AGENT_STATE))
            .set(field("current_state"), stateToSet.name())
            .set(field("last_transition_time"), System.currentTimeMillis())
            .where(field(AGENT_TYPE).eq(agentType))
            .andNot(
                field("current_state")
                    .in(STATES_TO_IGNORE_ON_EXISTING.stream().map(State::name).toList()))
            .execute();
      }
    } catch (Exception e) {
      log.error(
          "Failed to schedule agent {}.  THIS MAY still run... but there'll be no data in the schedule table to report it!",
          agentType,
          e);
    }
  }

  /** Count of agents per state - primarily for observability gauges. */
  public Map<String, Long> countAgentsByState() {
    return jooq.select(field("current_state"), count())
        .from(table(PUBSUB_AGENT_STATE))
        .groupBy(field("current_state"))
        .fetchMap(
            row -> row.get("current_state", String.class), row -> row.get(count()).longValue());
  }

  public List<AgentState> listAgentsFilteredWhereIn(Set<State> filterList) {
    try {
      Condition stateFilter =
          (filterList == null || filterList.isEmpty())
              ? noCondition()
              : field("current_state").in(filterList.stream().map(State::name).toList());
      return jooq.selectFrom(table(PUBSUB_AGENT_STATE))
          .where(stateFilter)
          .orderBy(field("last_execution_time"))
          .fetch()
          .map(agentStateMapper);
    } catch (Exception e) {
      log.error("Unable to query the list of agents!", e);
      throw new RuntimeException(e);
    }
  }

  public enum State {
    NOT_STARTED,
    FAILED,
    PENDING,
    RUNNING,
    FINISHED,
    DELETED
  }

  @Data
  @Builder
  public static class AgentState {
    @Nonnull private String agentType;
    @Nonnull private String currentState;
    private long lastTransitionTime;
    private long lastExecutionTime;
    // Used for determining max allowed time.
    private long lastDuration;
    // Useful metric for knowing HOW much data was processed.  Allows VERY easy
    // looking up how much activity an agent has on the endpoint.
    private long dataProcessed;
  }
}

package com.netflix.spinnaker.cats.pubsub;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.netflix.spinnaker.cats.agent.Agent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class StateMachine {
  public static final String PUBSUB_AGENT_STATE = "pubsub_agent_state";
  public static final String AGENT_TYPE = "agent_type";
  private static final int MAX_DURATION_FOR_AN_AGENT =
      7200; // up to 2 hours.  NO AGENT should EVER take this long.  WE HOPE!

  @Autowired private DSLContext jooq;

  public AgentState getAgent(String agentType) {
    try (var result =
        jooq.selectFrom(table(PUBSUB_AGENT_STATE)).where(field(AGENT_TYPE).eq(agentType))) {
      return result.fetch().into(AgentState.class).stream().findFirst().orElse(null);
    }
  }

  /** The assumption is that the agent HAS previously existed. WE DO NOT INSERT NEW AGENTS HERE. */
  public void acquireLock(Agent agent, int timeOutSeconds, State stateToApply) throws Exception {
    log.debug(
        "Acquiring lock for agent {} with a timeout of {} seconds",
        agent.getAgentType(),
        timeOutSeconds);
    try (ResultSet lockRecord =
        jooq.selectFrom(table(PUBSUB_AGENT_STATE))
            .where(field(AGENT_TYPE).eq(agent.getAgentType()))
            .forUpdate()
            .resultSetConcurrency(ResultSet.CONCUR_UPDATABLE)
            .queryTimeout(
                Integer.min(
                    timeOutSeconds,
                    MAX_DURATION_FOR_AN_AGENT)) // Since we use Integer.max_value for duration this
            // can go beyond an int...
            .fetchResultSet()) {
      if (lockRecord.next()) {
        lockRecord.updateString("current_state", stateToApply.name());
        lockRecord.updateRow();
      }
    } catch (SQLException e) {
      log.error("Unable to aquire lock!", e);
    }
  }

  /**
   * Allow updating the state of a specific agent AS LONG AS It's not marked for deletion.
   *
   * @param agentType
   * @param stateToSet
   * @return
   * @throws Exception
   */
  public int changeStateUnlessMarkedForDeletion(String agentType, State stateToSet) {
    return jooq.update(table(PUBSUB_AGENT_STATE))
        .set(field("current_state"), stateToSet.name())
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

  public static final Set<State> STATES_TO_IGNORE_ON_EXISTING =
      Set.of(State.DELETED, State.PENDING, State.FINISHED, State.RUNNING);

  public void createOrUpdateAgent(String agentType, State stateToSet) {
    // Get last COMPLETE date.  Get agent interval if scheduler aware.  IF interval is AFTER
    // complete date, queue it.
    try (@NotNull
        ResultSet lockRecord =
            jooq.select(
                    field("agent_type"),
                    field("current_state"),
                    field("last_execution_time"),
                    field("last_transition_time"),
                    field("last_duration"))
                .from(table(PUBSUB_AGENT_STATE))
                .where(field(AGENT_TYPE).eq(agentType))
                .forUpdate()
                .resultSetConcurrency(ResultSet.CONCUR_UPDATABLE)
                .fetchResultSet()) {

      if (lockRecord.next()) {
        // already exists... so we probably are rescheduling an agent.  Some of the providers to an
        // unschedule/reschedule to reschedule various agents.
        if (!STATES_TO_IGNORE_ON_EXISTING.contains(
            State.valueOf(lockRecord.getString("current_state")))) {
          // Never executed... so set some defaults for the next poll cycle to queue it as needed
          lockRecord.updateString("current_state", stateToSet.name());
          lockRecord.updateLong("last_transition_time", System.currentTimeMillis());
          lockRecord.updateRow();
        }
      } else {
        lockRecord.moveToInsertRow();
        lockRecord.updateString(AGENT_TYPE, agentType);
        lockRecord.updateLong("last_execution_time", 0);
        lockRecord.updateLong("last_duration", Integer.MAX_VALUE);
        lockRecord.updateLong("last_transition_time", System.currentTimeMillis());
        lockRecord.updateString("current_state", stateToSet.name());
        lockRecord.insertRow();
      }
    } catch (Exception e) {
      log.error(
          "Failed to schedule agent {}.  THIS MAY still run... but there'll be no dataa in the schedule tame to report it!",
          agentType,
          e);
    }
  }

  public List<AgentState> listAgentsFilteredWhereIn(Set<State> filterList) {
    try (@NotNull var query = jooq.selectFrom(table(PUBSUB_AGENT_STATE))) {
      if (filterList != null && !filterList.isEmpty()) {
        query.where(field("current_state").in(filterList.stream().map(State::name).toList()));
      }
      query.orderBy(field("last_execution_time"));
      return query.fetch().into(AgentState.class);
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
    @Nonnull private State currentState;
    private long lastTransitionTime;
    private long lastExecutionTime;
    // Used for determining max allowed time.
    private long lastDuration;
    // Useful metric for knowing HOW much data was processed.  Allows VERY easy
    // looking up how much activity an agent has on the endpoint.
    private long dataProcessed;
  }
}

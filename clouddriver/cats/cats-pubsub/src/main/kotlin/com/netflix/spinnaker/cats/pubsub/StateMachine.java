package com.netflix.spinnaker.cats.pubsub;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.netflix.spinnaker.cats.agent.Agent;
import java.sql.ResultSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
  @Autowired private DSLContext jooq;

  public AgentState getAgent(String agentType) {
    try (var result =
        jooq.selectFrom(table(PUBSUB_AGENT_STATE)).where(field(AGENT_TYPE).eq(agentType))) {
      return result.fetch().into(AgentState.class).stream().findFirst().orElse(null);
    }
  }

  /** The assumption is that the agent HAS previously existed. WE DO NOT INSERT NEW AGENTS HERE. */
  public void acquireLock(Agent agent, long timeOutMs, State stateToApply) throws Exception {
    @NotNull
    ResultSet lockRecord =
        jooq.selectFrom(table(PUBSUB_AGENT_STATE))
            .where(field(AGENT_TYPE).eq(agent.getAgentType()))
            .forUpdate()
            .queryTimeout((int) (timeOutMs / 1000))
            .fetchResultSet();
    if (lockRecord.next()) {
      lockRecord.updateString("current_state", stateToApply.name());
    }
  }

  /**
   * Allow updating the state of a specific agent AS LONG AS It's not marked for deletion.
   *
   * @param agent
   * @param stateToSet
   * @return
   * @throws Exception
   */
  public int changeStateUnlessMarkedForDeletion(Agent agent, State stateToSet) {
    return jooq.update(table(PUBSUB_AGENT_STATE))
        .set(field("current_state"), stateToSet)
        .where(AGENT_TYPE, agent.getAgentType())
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
            .where(AGENT_TYPE, agent.getAgentType())
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

  public void createOrUpdateAgent(Agent agent, State stateToSet) throws Exception {
    // Get last COMPLETE date.  Get agent interval if scheduler aware.  IF interval is AFTER
    // complete date, queue it.
    try {
      @NotNull
      ResultSet lockRecord =
          jooq.selectFrom(PUBSUB_AGENT_STATE)
              .where(AGENT_TYPE, agent.getAgentType())
              .forUpdate()
              .fetchResultSet();
      if (lockRecord.next()) {
        // already exists... so we probably are rescheduling an agent.  Some of the providers to an
        // unschedule/reschedule to reschedule various agents.
        if (lockRecord.getString("current_state").equals(State.DELETED.name())) {
          lockRecord.updateString("current_state", stateToSet.name());
        }
      } else {
        lockRecord.moveToInsertRow();
        lockRecord.updateString(AGENT_TYPE, agent.getAgentType());
        lockRecord.updateLong("last_execution_time", System.currentTimeMillis());
        lockRecord.updateString("current_state", stateToSet.name());
      }
    } catch (Exception e) {
      log.error(
          "Failed to schedule agent {}.  THIS MAY still run... but there'll be no dataa in the schedule tame to report it!",
          agent.getAgentType(),
          e);
    }
  }

  public List<AgentState> listAgents() {
    return jooq.selectFrom(table(PUBSUB_AGENT_STATE)).fetch().into(AgentState.class);
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
    @Nullable private State previousState;
    private long lastTransitionTime;
    private long lastExecutionTime;
    private long lastDuration; // Used for determining max allowed time.
    private long
        dataProcessed; // Useful metric for knowing HOW much data was processed.  ALllows VERY eays
    // looking up how much activity an agent has on the endpoint.

    public static AgentState from(Agent agent) {
      return builder()
          .agentType(agent.getAgentType())
          .currentState(State.NOT_STARTED)
          .previousState(null)
          .lastTransitionTime(System.currentTimeMillis())
          .lastExecutionTime(0)
          .lastDuration(0)
          .dataProcessed(0)
          .build();
    }
  }
}

package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.util.List;

@Component
@Log4j2
public class StateMachine {
  @Autowired
  private DSLContext jooq;


  public long getLastExecutionTime(String agentType) {
    return Integer.MAX_VALUE;
  }

  /*
  The assumption is that the agent HAS previously existed.  IF NOT, we'll insert at this point.
   */
  public void acquireLock(Agent agent, long timeOutMs, State stateToApply) throws Exception {
    @NotNull ResultSet lockRecord = jooq.selectFrom("pubsub_agent_state").where("agent_type", agent.getAgentType()).forUpdate().queryTimeout((int) (timeOutMs / 1000)).fetchResultSet();
    if (lockRecord.next()) {
      lockRecord.updateString("current_state", stateToApply.name());
    } else {
      lockRecord.moveToInsertRow();
      lockRecord.updateString("agent_type", agent.getAgentType());
      lockRecord.updateLong("last_execution_time", System.currentTimeMillis());
      lockRecord.updateString("current_state", stateToApply.name());
    }
  }

  public void releaseLock(Agent agent, State stateToSet) throws Exception {
    storeAgentState(agent, stateToSet);
  }

  public void storeAgentState(Agent agent, State stateToSet) throws Exception {
    // Get last COMPLETE date.  Get agent interval if scheduler aware.  IF interval is AFTER complete date, queue it.
    @NotNull ResultSet lockRecord = jooq.selectFrom("pubsub_agent_state").where("agent_type", agent.getAgentType()).forUpdate().fetchResultSet();
    if (lockRecord.next()) {
      lockRecord.updateString("current_state", stateToSet.name());
    } else {
      lockRecord.moveToInsertRow();
      lockRecord.updateString("agent_type", agent.getAgentType());
      lockRecord.updateLong("last_execution_time", System.currentTimeMillis());
      lockRecord.updateString("current_state", stateToSet.name());
    }
  }

  public void remove(Agent agent) {
    // we keep the agent IN the table marked as "DELETED" so that we can on agent COMPLETION (if already running) know that it's marked for deletion, and skip writing data, just let it complete
    // ONLY mark a row to be deleted if it wasn't recently set to be deleted.  This handles repeated "remove" calls.
    int rowsDeleted = jooq.update("pubsub_agent_state").set("current_state", StateMachine.State.DELETED.name()).where("agent_type", agent.getAgentType()).andNot("current_state", StateMachine.State.DELETED.name()).execute();
    if (rowsDeleted != 1) {
      log.warn("Failed to mark agent {} from pubsub_agent_state as deleted.  This COULD be tied to it being in execution OR something else purged it... ", agent.getAgentType());
    }
  }
  public void delete(Agent agent) {
    // we keep the agent IN the table marked as "DELETED" so that we can on agent COMPLETION (if already running) know that it's marked for deletion, and skip writing data, just let it complete
    int rowsDeleted = jooq.deleteFrom("pubsub_agent_state").where("agent_type", agent.getAgentType()).execute();
    if (rowsDeleted != 1) {
      log.warn("Failed to mark agent {} from pubsub_agent_state as deleted.  This COULD be tied to it being in execution OR something else purged it... ", agent.getAgentType());
    }
  }


  public void scheduleAgent(Agent agent) {
    try {
      // Get last COMPLETE date.  Get agent interval if scheduler aware.  IF interval is AFTER complete date, queue it.
      @NotNull ResultSet lockRecord = jooq.selectFrom("pubsub_agent_state").where("agent_type", agent.getAgentType());
      if (!lockRecord.next()) {// ONLY put it in table if it doesn't exist
        lockRecord.moveToInsertRow();
        lockRecord.updateString("agent_type", agent.getAgentType());
        lockRecord.updateLong("last_execution_time", System.currentTimeMillis());
        lockRecord.updateString("current_state", State.NOT_STARTED.name());
      }
    }catch(Exception e) {
      log.error("Failed to schedule agent {}.  THIS MAY still run... but there'll be no dataa in the schedule tame to report it!", agent.getAgentType(), e);
    }
  }

  public List<AgentState> listAgents() {
    return null;
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
  public static class AgentState {
    @Nonnull
    private String agentType;
    @Nonnull
    private State currentState;
    @Nullable
    private State previousState;
    private long lastTransitionTime;
    private long lastExecutionTime;
    private long lastDuration; // Used for determining max allowed time.
    private long dataProcessed; // Useful metric for knowing HOW much data was processed.  ALllows VERY eays looking up how much activity an agent has on the endpoint.
  }

}

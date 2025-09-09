package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

// Uses messagelistener interfaces from redis by default, but supports others
@Log4j2
@Component
public class PubSubAgentRunner implements MessageListener {
  @Autowired private ProviderRegistry providerRegistry;
  @Autowired StateMachine stateMachine;
  @Autowired MeterRegistry meterRegistry;
  @Autowired AgentIntervalProvider intervalProvider;

  @Value("${cats.pubsub.percentMaxOverNormalDuration:1.5}")
  private double percentMaxOverNormalDuration = 1.5;

  public void onMessage(Message message, byte[] pattern) {
    String agentType = new String(message.getBody());
    log.debug("Starting on message for agent {}", agentType);
    Agent agent = providerRegistry.getAgentForProviderName(agentType);
    StateMachine.AgentState agentState = stateMachine.getAgent(agentType);
   int lastDurationWithBuffer = (int) (Duration.ofMillis(agentState.getLastDuration()).toSeconds()*percentMaxOverNormalDuration);
    // lock up until a duration.  IF we've exceeded the normal duration, then fail & release.
    try {
      // we WILL NOT process more than one of any given agentType.  IF on demand is processed first,
      // it'll happen first.
      // if it's a regular agent, it'll happen before.  We just don't want two at the SAME time.
      // There's a case where an agent is already running - we will lock and wait for completion of
      // that agent before running it again.
      StopWatch totalDuration = new StopWatch();
      totalDuration.start();
      if (agent != null) {
        stateMachine.acquireLock(agent, lastDurationWithBuffer, StateMachine.State.RUNNING);
        if (agent instanceof CachingAgent cacheExecutionAgent) {
          CachingAgent.CacheExecution cacheAgentExecution = (CachingAgent.CacheExecution) cacheExecutionAgent.getAgentExecution(providerRegistry);
          List<Tag> tags =
              List.of(
                  Tag.of("agentType", agentType),
                  Tag.of("state", StateMachine.State.FINISHED.name()));
          StopWatch timer = new StopWatch();
          timer.start();
          intervalProvider.getInterval(agent).getTimeout();
          // Run this up to the interval timeout and fail if it runs longer.
          CacheResult cacheResult = cacheAgentExecution.executeAgentWithoutStore(agent);
          int totalDataCached = cacheResult.getCacheResults().values().stream().map(Collection::size).reduce(0, Integer::sum);
          timer.stop();
          meterRegistry.gauge("cats.pubsub.execution.duration", tags, timer.getTotalTimeMillis());
          meterRegistry.gauge(
              "cats.pubsub.execution.size", tags, totalDataCached);

          timer = new StopWatch();
          timer.start();
          cacheAgentExecution.storeAgentResult(agent, cacheResult);
          timer.stop();
          meterRegistry.gauge(
              "cats.pubsub.execution.storagetime", tags, timer.getTotalTimeMillis());
          meterRegistry
              .counter(
                  "cats.pubsub.agents.processed",
                  List.of(
                      Tag.of("agentType", agentType),
                      Tag.of("state", StateMachine.State.FINISHED.name())))
              .increment();
          totalDuration.stop();
          stateMachine.markAgentCompleted(agent.getAgentType(), totalDuration.getTotalTimeMillis(), totalDataCached);
        } else {
          agent.getAgentExecution(providerRegistry).executeAgent(agent);
          //UNFORTUNATELY... unless this is a  CachingAgent.CacheExecution agent, we won't know how much data it processed.  There
          // are a FEW agents that are NOT CacheExecution agents at this time.
          log.info("We completed {} but since it's not a CacheExecution agent we can't track how much data it processed.", agent.getAgentType());
          totalDuration.stop();
          stateMachine.markAgentCompleted(agent.getAgentType(), totalDuration.getTotalTimeMillis(), -1);
        }
      }
    } catch (SQLTimeoutException exceeded) {
      log.error(
          "Exceeded max duration for account {} of {} (with buffer) while trying to get a unique lock to run the agent.  There may be a hung agent someplace",
          agentType,
        lastDurationWithBuffer);
      meterRegistry
          .counter(
              "cats.pubsub.agents.processed",
              List.of(
                  Tag.of("accountType", agentType),
                  Tag.of("state", StateMachine.State.FAILED.name())))
          .increment();
      stateMachine.changeStateUnlessMarkedForDeletion(agent.getAgentType(), StateMachine.State.FAILED);
      // SO MANY of these will then trigger
      throw new RuntimeException(exceeded);
    } catch (ExecutionDurationExceeded exceeded) {
      meterRegistry
          .counter(
              "cats.pubsub.agents.processed",
              List.of(
                  Tag.of("accountType", agentType),
                  Tag.of("state", StateMachine.State.FAILED.name())))
          .increment();
      log.error(
          "Agent {} exceeded max duration for account {} of {} (with buffer).  This shouldn't norally happy if it's performing consistently.",
          agentType,
          agentType,
        lastDurationWithBuffer);
      stateMachine.changeStateUnlessMarkedForDeletion(agent.getAgentType(), StateMachine.State.FAILED);
      // SO MANY of these will then trigger
      throw new RuntimeException(exceeded);
    } catch (Exception e) {
      stateMachine.changeStateUnlessMarkedForDeletion(agent.getAgentType(), StateMachine.State.FAILED);
      log.error(
          "Agent {} failed to run due to an exception.  LIKELY an error or similar.  Setting state to FAILED, will do backoffs before rescheduling. THIS MEANS the system MAY be running multiples of this agent or other odd state!",
          agentType);
      throw new RuntimeException(e);
    }
  }
}

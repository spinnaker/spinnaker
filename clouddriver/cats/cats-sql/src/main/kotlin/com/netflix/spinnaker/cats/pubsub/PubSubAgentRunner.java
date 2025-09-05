package com.netflix.spinnaker.cats.pubsub;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.sql.SQLTimeoutException;
import java.util.List;

// Uses messagelistener interfaces from redis by default, but supports others
@Log4j2
@Component
public class PubSubAgentRunner {
  @Autowired
  private ProviderRegistry providerRegistry;
  @Autowired
  StateMachine stateMachine;
  @Autowired
  MeterRegistry meterRegistry;

  @Value("${cats.pubsub.percentMaxOverNormalDuration:1.5}")
  private double percentMaxOverNormalDuration = 1.5;

  public void onMessage(String agentType) throws Exception {
    Agent agent = providerRegistry.getAgentForProviderName(agentType);
    long lastExecutionTimeWithBuffer = (long)(stateMachine.getLastExecutionTime(agentType) * percentMaxOverNormalDuration);
    // lock up until a duration.  IF we've exceeded the normal duration, then fail & release.
    try {
      // we WILL NOT process more than one of any given agentType.  IF on demand is processed first, it'll happen first.
      // if it's a regular agent, it'll happen before.  We just don't want two at the SAME time.
      // There's a case where an agent is already running - we will lock and wait for completion of that agent before running it again.
      if (agent != null) {
        stateMachine.acquireLock(agent, lastExecutionTimeWithBuffer, StateMachine.State.RUNNING);
        if (agent instanceof CachingAgent.CacheExecution cacheExecutionAgent) {
          List<Tag> tags = List.of(Tag.of("accountType", agentType), Tag.of("state", StateMachine.State.FINISHED.name()));
          StopWatch timer = new StopWatch();
          timer.start();
          CacheResult cacheResult = cacheExecutionAgent.executeAgentWithoutStore(agent);
          timer.stop();
          meterRegistry.gauge("cats.pubsub.cacheResults.duration.execution", tags, timer.getTotalTimeMillis());
          meterRegistry.gauge("cats.pubsub.cacheResults.size", tags, cacheResult.getCacheResults().size());

          timer = new StopWatch();
          timer.start();
          cacheExecutionAgent.storeAgentResult(agent, cacheResult);
          timer.stop();
          meterRegistry.gauge("cats.pubsub.cacheResults.duration.storage", tags, timer.getTotalTimeMillis());
          meterRegistry.counter("cats.pubsub.agents.processed", List.of(Tag.of("accountType", agentType), Tag.of("state", StateMachine.State.FINISHED.name()))).increment();
        } else {
          agent.getAgentExecution(providerRegistry).executeAgent(agent);
        }
        stateMachine.releaseLock(agent, StateMachine.State.FINISHED);
      }
      );
    } catch (SQLTimeoutException exceeded) {
      log.error("Exceeded max duration for account {} of {} (with buffer) while trying to get a unique lock to run the agent.  There may be a hung agent someplace", agentType, lastExecutionTimeWithBuffer);
      meterRegistry.counter("cats.pubsub.agents.processed", List.of(Tag.of("accountType", agentType), Tag.of("state", StateMachine.State.FAILED.name()))).increment();
      stateMachine.releaseLock(agent, StateMachine.State.FAILED);
      // SO MANY of these will then trigger
      throw exceeded;
    } catch (ExecutionDurationExceeded exceeded) {
      meterRegistry.counter("cats.pubsub.agents.processed", List.of(Tag.of("accountType", agentType), Tag.of("state", StateMachine.State.FAILED.name()))).increment();
      log.error("Agent {} exceeded max duration for account {} of {} (with buffer).  This shouldn't norally happy if it's performing consistently.", agentType, agentType, lastExecutionTimeWithBuffer);
      stateMachine.releaseLock(agent, StateMachine.State.FAILED);
      // SO MANY of these will then trigger
      throw exceeded;
    } catch (Exception e){
      stateMachine.releaseLock(agent, StateMachine.State.FAILED);
        log.error("Agent {} failed to run due to an exception.  LIKELY an error or similar.  Setting state to FAILED, will do backoffs before rescheduling. THIS MEANS the system MAY be running multiples of this agent or other odd state!", agentType);
        throw e;
      }

    }

  }

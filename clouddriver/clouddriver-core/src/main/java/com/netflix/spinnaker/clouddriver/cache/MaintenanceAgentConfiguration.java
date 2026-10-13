/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cache;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CompositeExecutionInstrumentation;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.agent.NoopExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link MaintenanceAgent}s to a {@link MaintenanceAgentScheduler} that is independent of
 * CATS and of {@code caching.write-enabled}. Enabled by default.
 *
 * <p>The {@link MaintenanceLock} is contributed by the data store module (SQL). Startup fails if
 * maintenance agents exist without one.
 */
@Configuration
@ConditionalOnProperty(value = "maintenance-agents.enabled", matchIfMissing = true)
public class MaintenanceAgentConfiguration {
  private static final long FALLBACK_INTERVAL_MILLIS = 60_000;
  private static final long FALLBACK_ERROR_INTERVAL_MILLIS = 60_000;
  private static final long FALLBACK_TIMEOUT_MILLIS = 300_000;

  @Bean
  MaintenanceAgents maintenanceAgents(
      ObjectProvider<List<MaintenanceAgent>> agents,
      ObjectProvider<MaintenanceLock> lock,
      DynamicConfigService dynamicConfigService,
      ObjectProvider<List<ExecutionInstrumentation>> instrumentations,
      @Value("${maintenance-agents.lock-poll-interval-ms:30000}") long lockPollIntervalMillis,
      @Value("${maintenance-agents.max-threads:4}") int maxThreads) {
    List<MaintenanceAgent> agentList = agents.getIfAvailable(List::of);
    List<ExecutionInstrumentation> instrumentationList = instrumentations.getIfAvailable(List::of);
    MaintenanceLock maintenanceLock = lock.getIfAvailable();
    if (!agentList.isEmpty() && maintenanceLock == null) {
      throw new IllegalStateException(
          "Maintenance agents exist ("
              + agentList.stream().map(Agent::getAgentType).collect(Collectors.toList())
              + ") but no MaintenanceLock is configured; maintenance agents require sql.enabled=true"
              + " or an explicit MaintenanceLock bean");
    }
    return new MaintenanceAgents(
        agentList,
        maintenanceLock,
        // Only used when an agent returns -1 for its interval, error interval or timeout.
        new CustomSchedulableAgentIntervalProvider(
            FALLBACK_INTERVAL_MILLIS, FALLBACK_ERROR_INTERVAL_MILLIS, FALLBACK_TIMEOUT_MILLIS),
        dynamicConfigService,
        instrumentationList.isEmpty()
            ? new NoopExecutionInstrumentation()
            : new CompositeExecutionInstrumentation(instrumentationList),
        lockPollIntervalMillis,
        maxThreads);
  }

  /**
   * Owns the {@link MaintenanceAgentScheduler}: schedules every agent once all singletons exist and
   * shuts the executor down on close. Not an AgentScheduler, so CATS wiring is unaffected.
   */
  public static class MaintenanceAgents implements SmartInitializingSingleton, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceAgents.class);

    private final List<MaintenanceAgent> agents;
    private final ExecutionInstrumentation instrumentation;
    private final MaintenanceAgentScheduler scheduler;
    private final ScheduledExecutorService executor;

    MaintenanceAgents(
        List<MaintenanceAgent> agents,
        MaintenanceLock lock,
        AgentIntervalProvider intervalProvider,
        DynamicConfigService dynamicConfigService,
        ExecutionInstrumentation instrumentation,
        long lockPollIntervalMillis,
        int maxThreads) {
      this.agents = agents;
      this.instrumentation = instrumentation;
      if (agents.isEmpty()) {
        this.executor = null;
        this.scheduler = null;
      } else {
        this.executor =
            Executors.newScheduledThreadPool(
                Math.max(1, Math.min(agents.size(), maxThreads)),
                new ThreadFactoryBuilder()
                    .setNameFormat(MaintenanceAgentScheduler.class.getSimpleName() + "-%d")
                    .setDaemon(true)
                    .build());
        this.scheduler =
            new MaintenanceAgentScheduler(
                lock, intervalProvider, dynamicConfigService, executor, lockPollIntervalMillis);
      }
    }

    @Override
    public void afterSingletonsInstantiated() {
      if (scheduler == null) {
        return;
      }
      for (Agent agent : agents) {
        log.info("Scheduling maintenance agent {}", agent.getAgentType());
        scheduler.schedule(agent, agent.getAgentExecution(null), instrumentation);
      }
    }

    @Override
    public void destroy() {
      if (executor != null) {
        executor.shutdownNow();
      }
    }

    /** Visible for testing. */
    boolean hasScheduler() {
      return scheduler != null;
    }
  }
}

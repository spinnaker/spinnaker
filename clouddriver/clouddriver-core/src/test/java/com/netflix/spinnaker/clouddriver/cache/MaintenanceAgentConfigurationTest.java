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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MaintenanceAgentConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(MaintenanceAgentConfiguration.class, BaseConfig.class);

  @Configuration
  static class BaseConfig {
    @Bean
    DynamicConfigService dynamicConfigService() {
      DynamicConfigService svc = mock(DynamicConfigService.class);
      org.mockito.Mockito.when(
              svc.getConfig(
                  org.mockito.ArgumentMatchers.eq(String.class),
                  org.mockito.ArgumentMatchers.anyString(),
                  org.mockito.ArgumentMatchers.anyString()))
          .thenReturn("");
      return svc;
    }
  }

  @Configuration
  static class SqlLikeLockConfig {
    @Bean
    MaintenanceLock sqlStandInLock() {
      MaintenanceLock lock = mock(MaintenanceLock.class);
      org.mockito.Mockito.when(
              lock.tryAcquire(
                  org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
          .thenReturn(true);
      return lock;
    }
  }

  static final AtomicInteger RUNS = new AtomicInteger();

  @Configuration
  static class AgentConfig {
    @Bean
    MaintenanceAgent testMaintenanceAgent() {
      return new MaintenanceAgent() {
        @Override
        public void run() {
          RUNS.incrementAndGet();
        }

        @Override
        public String getAgentType() {
          return "ConfigTestAgent";
        }

        @Override
        public String getProviderName() {
          return "test";
        }

        @Override
        public long getPollIntervalMillis() {
          return 60_000;
        }

        @Override
        public long getTimeoutMillis() {
          return 60_000;
        }
      };
    }
  }

  @Test
  void enabledByDefaultAndNeverRegistersAnAgentScheduler() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(MaintenanceAgentConfiguration.MaintenanceAgents.class);
          assertThat(ctx).doesNotHaveBean(AgentScheduler.class);
          assertThat(ctx).doesNotHaveBean(MaintenanceAgentScheduler.class);
        });
  }

  @Test
  void disabledWhenPropertyFalse() {
    runner
        .withPropertyValues("maintenance-agents.enabled=false")
        .run(
            ctx -> {
              assertThat(ctx)
                  .doesNotHaveBean(MaintenanceAgentConfiguration.MaintenanceAgents.class);
              assertThat(ctx).doesNotHaveBean(MaintenanceLock.class);
            });
  }

  @Test
  void noAgentsMeansNothingScheduled() {
    runner.run(
        ctx ->
            assertThat(
                    ctx.getBean(MaintenanceAgentConfiguration.MaintenanceAgents.class)
                        .hasScheduler())
                .isFalse());
  }

  @Test
  void agentsAreScheduledOnceSingletonsAreInitializedAndStoppedOnClose() throws Exception {
    RUNS.set(0);
    runner
        .withUserConfiguration(AgentConfig.class, SqlLikeLockConfig.class)
        .run(
            ctx -> {
              assertThat(
                      ctx.getBean(MaintenanceAgentConfiguration.MaintenanceAgents.class)
                          .hasScheduler())
                  .isTrue();
              long deadline = System.currentTimeMillis() + 5000;
              while (RUNS.get() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
              }
              assertThat(RUNS.get()).isEqualTo(1); // lock always acquired; tick period 30s
              assertThat(ctx).doesNotHaveBean(AgentScheduler.class);
            });
  }

  @Test
  void failsFastWhenAgentsExistWithoutALock() {
    runner
        .withUserConfiguration(AgentConfig.class)
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure()).hasStackTraceContaining("no MaintenanceLock");
            });
  }
}

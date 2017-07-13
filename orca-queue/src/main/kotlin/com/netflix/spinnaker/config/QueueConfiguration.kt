/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.config.OrcaConfiguration.applyThreadPoolMetrics
import com.netflix.spinnaker.orca.log.BlackholeExecutionLogRepository
import com.netflix.spinnaker.orca.log.ExecutionLogRepository
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.handler.DeadMessageHandler
import com.netflix.spinnaker.orca.q.memory.InMemoryQueue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Clock
import java.util.concurrent.Executor

@Configuration
@ComponentScan(basePackages = arrayOf("com.netflix.spinnaker.orca.q", "com.netflix.spinnaker.orca.log", "com.netflix.spinnaker.orca.q.trafficshaping"))
@EnableScheduling
open class QueueConfiguration {
  @Bean @ConditionalOnMissingBean(Clock::class)
  open fun systemClock(): Clock = Clock.systemDefaultZone()

  @Bean(name = arrayOf("queueImpl")) @ConditionalOnMissingBean(Queue::class)
  open fun inMemoryQueue(clock: Clock, deadMessageHandler: DeadMessageHandler, publisher: ApplicationEventPublisher) =
    InMemoryQueue(
      clock = clock,
      deadMessageHandler = deadMessageHandler::handle,
      publisher = publisher
    )

  @Bean @ConditionalOnMissingBean(ExecutionLogRepository::class)
  open fun executionLogRepository(): ExecutionLogRepository = BlackholeExecutionLogRepository()

  @Bean
  open fun messageHandlerPool(registry: Registry): ThreadPoolTaskExecutor =
    applyThreadPoolMetrics(
      registry,
      ThreadPoolTaskExecutor().apply {
        corePoolSize = 20
        maxPoolSize = 150
      },
      "messageHandler"
    )

  /**
   * This overrides Spring's default application event multicaster as we need
   * to _guarantee_ that exceptions thrown by listeners or just listeners taking
   * a long time to do stuff do not affect the processing of the queue.
   */
  @Bean
  open fun applicationEventMulticaster(
    @Qualifier("applicationEventTaskExecutor") taskExecutor: ThreadPoolTaskExecutor
  ): ApplicationEventMulticaster =
    SimpleApplicationEventMulticaster().apply {
      setTaskExecutor(taskExecutor)
      // TODO: should set an error handler as well
    }

  @Bean open fun applicationEventTaskExecutor(registry: Registry): ThreadPoolTaskExecutor =
    applyThreadPoolMetrics(
      registry,
      ThreadPoolTaskExecutor().apply {
        corePoolSize = 20
        maxPoolSize = 20
      },
      "applicationEventMulticaster"
    )
}

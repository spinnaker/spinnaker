/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.api.test

import com.netflix.spinnaker.orca.notifications.AlwaysUnlockedNotificationClusterLock
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository
import com.netflix.spinnaker.orca.q.handler.DeadMessageHandler
import com.netflix.spinnaker.orca.q.pending.InMemoryPendingExecutionService
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.memory.InMemoryQueue
import com.netflix.spinnaker.q.metrics.EventPublisher
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock

/**
 * Configuration for Orca integration tests, configuring the application to
 * use in-memory variants of various backends.
 */
@TestConfiguration
class TestKitConfiguration {

  /**
   * Primary must be used here because Keiko will attempt to wire up a NoOpQueue, for whatever reason.
   */
  @Primary
  @Bean
  fun memoryQueue(clock: Clock, publisher: EventPublisher, deadMessageHandler: DeadMessageHandler) =
    InMemoryQueue(clock = clock, publisher = publisher, deadMessageHandlers = listOf(deadMessageHandler))

  @Bean
  fun memoryExecutionRepository(): ExecutionRepository =
    InMemoryExecutionRepository()

  @Bean
  fun memoryNotificationClusterLock(): NotificationClusterLock =
    AlwaysUnlockedNotificationClusterLock()

  @Bean
  fun memoryPendingExecutionService(): PendingExecutionService =
    InMemoryPendingExecutionService()
}

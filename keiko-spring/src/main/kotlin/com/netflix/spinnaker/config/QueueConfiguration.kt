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
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.EnabledActivator
import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.QueueExecutor
import com.netflix.spinnaker.q.QueueProcessor
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.metrics.NoopEventPublisher
import com.netflix.spinnaker.q.metrics.QueueMetricsPublisher
import com.netflix.spinnaker.q.metrics.QueueMonitor
import java.time.Clock
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableConfigurationProperties(QueueProperties::class, ObjectMapperSubtypeProperties::class)
@ComponentScan(basePackages = ["com.netflix.spinnaker.q"])
@EnableScheduling
class QueueConfiguration {

  @Bean
  @ConditionalOnMissingBean(Clock::class)
  fun systemClock(): Clock = Clock.systemDefaultZone()

  @Bean
  fun messageHandlerPool(queueProperties: QueueProperties) =
    ThreadPoolTaskExecutor().apply {
      threadNamePrefix = queueProperties.handlerThreadNamePrefix
      corePoolSize = queueProperties.handlerCorePoolSize
      maxPoolSize = queueProperties.handlerMaxPoolSize
      setQueueCapacity(0)
    }

  @Bean
  @ConditionalOnMissingBean(QueueExecutor::class)
  fun queueExecutor(messageHandlerPool: ThreadPoolTaskExecutor) =
    object : QueueExecutor<ThreadPoolTaskExecutor>(messageHandlerPool) {
      override fun hasCapacity() =
        executor.threadPoolExecutor.run {
          activeCount < maximumPoolSize
        }

      override fun availableCapacity() =
        executor.threadPoolExecutor.run {
          maximumPoolSize - activeCount
        }
    }

  @Bean
  fun queueProcessor(
    queue: Queue,
    executor: QueueExecutor<*>,
    handlers: Collection<MessageHandler<*>>,
    activators: List<Activator>,
    publisher: EventPublisher,
    queueProperties: QueueProperties,
    deadMessageHandler: DeadMessageCallback
  ) = QueueProcessor(
    queue,
    executor,
    handlers,
    activators,
    publisher,
    deadMessageHandler,
    queueProperties.fillExecutorEachCycle,
    Duration.ofSeconds(queueProperties.requeueDelaySeconds),
    Duration.ofSeconds(queueProperties.requeueMaxJitterSeconds)
  )

  @Bean
  fun enabledActivator(queueProperties: QueueProperties) = EnabledActivator(queueProperties.enabled)

  @Bean
  @ConditionalOnProperty("queue.metrics.enabled", havingValue = "true", matchIfMissing = true)
  fun queueMonitor(
    registry: Registry,
    clock: Clock,
    queue: MonitorableQueue
  ) = QueueMonitor(registry, clock, queue)

  @Bean
  @ConditionalOnProperty("queue.metrics.enabled", havingValue = "true", matchIfMissing = true)
  fun queueMetricsPublisher(
    registry: Registry,
    clock: Clock
  ): EventPublisher =
    QueueMetricsPublisher(registry, clock)

  @Bean
  @ConditionalOnMissingBean(EventPublisher::class)
  fun queueEventPublisher(): EventPublisher =
    NoopEventPublisher()
}

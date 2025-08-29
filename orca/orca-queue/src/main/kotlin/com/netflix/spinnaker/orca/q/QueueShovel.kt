/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import java.time.Duration
import java.time.Instant
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

private const val rateMs: Long = 5_000
private const val workDurationMs = (0.95 * rateMs).toLong()

/**
 * The QueueShovel can be used to migrate from one queue implementation to another without an
 * operator needing to perform any substantial external work.
 *
 * In the case of a RedisQueue, when a previous Redis connection is configured, this shovel
 * would be wired up to move messages off the old Redis server and onto the new one as the
 * messages become available for processing.
 */
class QueueShovel(
  private val queue: Queue,
  private val previousQueue: Queue,
  private val registry: Registry,
  private val activator: Activator,
  private val config: DynamicConfigService,
  private val executionRepository: ExecutionRepository?
) {
  private val log = LoggerFactory.getLogger(javaClass)

  private val pollOpsRateId = registry.createId("orca.nu.shovel.pollOpsRate")
  private val shoveledMessageId = registry.createId("orca.nu.shovel.pushedMessageRate")
  private val shovelErrorId = registry.createId("orca.nu.shovel.pushedMessageErrorRate")

  @Scheduled(fixedRate = rateMs)
  fun migrateIfActive() {
    if (!isActive()) {
      return
    }

    log.info("Actively shoveling from $previousQueue to $queue")
    val workDuration = Duration.ofMillis(workDurationMs)
    val start = Instant.now()

    while (Duration.between(start, Instant.now()) < workDuration) {
      migrateOne()
      Thread.sleep(50)
    }
  }

  private fun isActive() = config.getConfig(Boolean::class.java, "queue.shovel.active", false) && activator.enabled

  fun migrateOne() {
    registry.counter(pollOpsRateId).increment()
    previousQueue.poll { message, ack ->
      try {
        log.debug("Shoveling message $message")

        // transfer the ownership _before_ pushing the message on the queue
        // we don't want a task handler running that message if the execution is not local
        transferOwnership(message)

        queue.push(message)
        ack.invoke()
        registry.counter(shoveledMessageId).increment()
      } catch (e: ExecutionNotFoundException) {
        // no need to log the stack trace on ExecutionNotFoundException, which can be somewhat expected
        log.error(
          "Failed shoveling message from previous queue to active (message: $message) " +
            "because of exception $e"
        )
        registry.counter(shovelErrorId).increment()
      } catch (e: Throwable) {
        log.error("Failed shoveling message from previous queue to active (message: $message)", e)
        registry.counter(shovelErrorId).increment()
      }
    }
  }

  private fun transferOwnership(message: Message) {
    if (executionRepository == null) {
      return
    }

    if (message !is ExecutionLevel) {
      log.warn("Message $message does not implement ExecutionLevel, can not inspect partition")
      return
    }

    // don't catch exceptions on retrieve/store (e.g. ExecutionNotFoundException), so that we can short-circuit shoveling
    // of this message
    val execution = executionRepository.retrieve(message.executionType, message.executionId)
    val isForeign = !executionRepository.handlesPartition(execution.partition)
    if (isForeign) {
      log.info(
        "Taking ownership of foreign execution ${execution.id} with partition '${execution.partition}'. " +
          "Setting partition to '${executionRepository.partition}'"
      )
      execution.partition = executionRepository.partition
      executionRepository.store(execution)
    }
  }

  @PostConstruct
  fun confirmShovelUsage() {
    log.info("${javaClass.simpleName} migrator from $previousQueue to $queue is enabled")
    if (executionRepository == null) {
      log.warn("${javaClass.simpleName} configured without an ExecutionRepository, won't be able to transfer ownership")
    }
  }
}

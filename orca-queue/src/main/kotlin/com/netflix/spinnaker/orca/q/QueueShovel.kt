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
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import javax.annotation.PostConstruct


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
  private val activator: Activator
) {
  private val log = LoggerFactory.getLogger(javaClass)

  private val pollOpsRateId = registry.createId("orca.nu.shovel.pollOpsRate")
  private val shoveledMessageId = registry.createId("orca.nu.shovel.pushedMessageRate")
  private val shovelErrorId = registry.createId("orca.nu.shovel.pushedMessageErrorRate")

  @Scheduled(fixedDelayString = "\${queue.shovel.poll-frequency.ms:500}")
  fun migrateOne() {
    activator.ifEnabled {
      registry.counter(pollOpsRateId).increment()
      previousQueue.poll { message, ack ->
        try {
          queue.push(message)
          ack.invoke()
          registry.counter(shoveledMessageId).increment()
        } catch (e: Throwable) {
          log.error("Failed shoveling message from previous queue to active (message: {})", message, e)
          registry.counter(shovelErrorId).increment()
        }
      }
    }
  }

  @PostConstruct
  fun confirmShovelUsage() =
    log.info("Running ${javaClass.simpleName} migrator")
}

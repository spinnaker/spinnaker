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
package com.netflix.spinnaker.keel.scheduler

import com.netflix.spinnaker.q.Queue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import javax.annotation.PostConstruct

/**
 * Starts the convergence schedule, and ensures that it stays scheduled through failures.
 */
@Component
@ConditionalOnExpression("\${scheduler.enabled:true}")
class QueueBackedSchedulerAgent(
  private val queue: Queue,
  @Value("\${scheduler.retry.onStart.ms:30000}") private val ensureSchedulerFrequency: Long
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct fun ensureSchedule() {
    log.info("Ensuring scheduler convergence task exists")
    queue.ensure(ScheduleConvergence(), Duration.ofMillis(ensureSchedulerFrequency))
  }

  @Scheduled(fixedDelayString = "\${scheduler.retry.frequency.ms:30000}")
  fun run() {
    queue.ensure(ScheduleConvergence(), Duration.ofSeconds(30))
  }
}

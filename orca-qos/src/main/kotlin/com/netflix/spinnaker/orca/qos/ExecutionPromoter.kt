/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.qos

import com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Marker interface if someone wants to override the default promoter.
 */
interface ExecutionPromoter

@Component
@ConditionalOnMissingBean(ExecutionPromoter::class)
class DefaultExecutionPromoter(
  private val executionRepository: ExecutionRepository,
  private val policies: List<PromotionPolicy>
) : ExecutionPromoter {

  private val log = LoggerFactory.getLogger(ExecutionPromoter::class.java)

  init {
    if (policies.isEmpty()) {
      throw NoPromotionPolicies("At least one PromotionPolicy must be defined")
    }
  }

  @Scheduled(fixedDelayString = "\${qos.executionPromoter.intervalMs:5000}")
  fun promote() {
    executionRepository.retrieveBufferedExecutions()
      .sortedByDescending { it.buildTime }
      .let {
        // TODO rz - This is all temporary mess and isn't meant to live long-term. I'd like to calculate until
        // result.finalized==true or the end of the list, then be able to pass all contributing source & reason pairs
        // into a log, with a zipped summary that would be saved into an execution's system notifications.
        var lastResult: PromotionResult? = null
        var candidates = it
        policies.forEach { policy ->
          val result = policy.apply(candidates)
          if (result.finalized) {
            return@let result
          }
          candidates = result.candidates
          lastResult = result
        }
        lastResult ?: PromotionResult(
          source =  javaClass.simpleName,
          candidates = candidates,
          finalized = true,
          reason = "No promotion policy resulted in an action"
        )
      }
      .also { result ->
        result.candidates.forEach {
          log.info("Promoting execution {} for work: {}", value("executionId", it.id), result.reason)
          executionRepository.updateStatus(it.id, NOT_STARTED)
        }
      }
  }

  private class NoPromotionPolicies(message: String) : BeanInitializationException(message)
}

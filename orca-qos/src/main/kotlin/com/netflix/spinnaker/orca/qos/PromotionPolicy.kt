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

import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.springframework.core.Ordered

/**
 * A policy responsible for determining what execution(s) should be promoted.
 *
 * PromotionPolicies can either reduce the list of candidates or re-order them, as well as short-circuit lower
 * precedence PromotionPolicies.
 */
interface PromotionPolicy : Ordered {

  fun apply(candidates: List<Execution>): PromotionResult

  override fun getOrder() = 0
}

/**
 * @param candidates A potentially reduced or re-ordered list of promotion candidates.
 * @param finalized Flag controlling whether or not the [candidates] are the final list of executions to promote.
 * Setting this this value to true will short-circuit lower-precedence [PromotionPolicy]s.
 * @param reason A human-friendly reason for the promotion result.
 */
data class PromotionResult(
  val candidates: List<Execution>,
  val finalized: Boolean,
  val reason: String
)

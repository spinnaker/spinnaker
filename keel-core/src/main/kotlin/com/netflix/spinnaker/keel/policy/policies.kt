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
package com.netflix.spinnaker.keel.policy

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.IntentPriority
import com.netflix.spinnaker.keel.Policy

@JsonTypeName("Enabled")
data class EnabledPolicy(
  val flag: Boolean = true
) : Policy()

/**
 * PriorityPolicy can be provided to an Intent to assign criticality. This allows end-users to self-define how mandatory
 * certain intents are compared to others.
 *
 * TODO rz - Allow users to self-manage priority of intents inside of their own defined buckets (like, by team).
 */
@JsonTypeName("Priority")
data class PriorityPolicy(
  val priority: IntentPriority = IntentPriority.NORMAL
) : Policy()

//@JsonTypeName("Delivery")
//data class DeliveryPolicy(
//  val backoffMultiplier: Float,
//  val convergeRate: Duration
//) : Policy()
//
//// TODO rz - kinds: "PreviousStateRollback" "RunOrchestrationRollback" "RunPipelineRollback" etc
//@JsonTypeName("Rollback")
//data class RollbackPolicy(
//) : Policy()
//
//@JsonTypeName("ExecutionWindow")
//data class ExecutionWindowPolicy(
//  override val matchers: MutableList<Matcher> = mutableListOf()
//) : Policy()

// TODO rz - Allow people to define if they're notified on changes, failures (how to configure notification channels?)
// Probably warrants a keel-echo module and just drop this in there.
//@JsonTypeName("Notification")
//data class NotificationPolicy(
//  val changes: Boolean,
//  val failures: Boolean
//) : Policy()

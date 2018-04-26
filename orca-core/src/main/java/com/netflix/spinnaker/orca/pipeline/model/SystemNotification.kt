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
package com.netflix.spinnaker.orca.pipeline.model

/**
 * Provides a user-facing notification of a system state of an [Execution].
 *
 * Each [SystemNotification] is treated as immutable and stored in an
 * append-only log. In order to finalize/cancel/dismiss a previous
 * notification, the [group] propertyshould be used in combination with
 * [closed], so that setting [closed] to true willhide other messages using
 * the same [group] value. In the case of a close record, the message should
 * include a reason why it is being closed.
 */
data class SystemNotification(
  val createdAt: Long,
  val group: String,
  val message: String,
  val closed: Boolean
)

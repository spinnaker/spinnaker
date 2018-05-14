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
package com.netflix.spinnaker.orca.notifications.scheduling

import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository

interface PollingAgentExecutionRepository : ExecutionRepository {

  fun retrieveAllApplicationNames(type: ExecutionType?): List<String>
  fun retrieveAllApplicationNames(type: ExecutionType?, minExecutions: Int): List<String>
  fun hasEntityTags(type: ExecutionType, id: String): Boolean
}

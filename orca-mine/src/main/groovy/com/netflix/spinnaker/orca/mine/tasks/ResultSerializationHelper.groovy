/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ResultSerializationHelper {
  public static final TypeReference<Map<String, ? extends Object>> JSON_TYPE = new TypeReference<Map<String, ? extends Object>>() {}

  @Autowired ObjectMapper objectMapper

  TaskResult result(ExecutionStatus exectionStatus, Map stageOutputs, Map globalOutputs = [:]) {
    Map<String, Object> stage = objectMapper.convertValue(stageOutputs, JSON_TYPE)
    Map<String, Object> global = objectMapper.convertValue(globalOutputs, JSON_TYPE)
    return new DefaultTaskResult(exectionStatus, stage, global)
  }
}

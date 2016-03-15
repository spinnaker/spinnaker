/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import groovy.transform.CompileStatic
import com.google.common.collect.ImmutableMap

@CompileStatic
final class DefaultTaskResult implements TaskResult {

  /**
   * A useful constant for a success result with no outputs.
   */
  public static
  final DefaultTaskResult SUCCEEDED = new DefaultTaskResult(ExecutionStatus.SUCCEEDED)

  final ExecutionStatus status
  final ImmutableMap<String, Serializable> outputs
  final ImmutableMap<String, Serializable> globalOutputs

  DefaultTaskResult(ExecutionStatus status) {
    this(status, [:], [:])
  }

  DefaultTaskResult(ExecutionStatus status,
                    Map<String, ? extends Object> stageOutputs,
                    Map<String, ? extends Object> globalOutputs) {
    this.status = status
    this.outputs = ImmutableMap.copyOf(stageOutputs)
    this.globalOutputs = ImmutableMap.copyOf(globalOutputs)
  }

  DefaultTaskResult(ExecutionStatus status,
                    Map<String, ? extends Object> stageOutputs) {
    this(status, stageOutputs, [:])
  }

  @Override
  ImmutableMap<String, Serializable> getStageOutputs() {
    return outputs
  }
}

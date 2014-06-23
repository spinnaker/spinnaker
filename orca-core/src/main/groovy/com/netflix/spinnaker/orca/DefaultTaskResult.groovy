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

  final TaskResult.Status status
  final ImmutableMap<String, Object> outputs

  DefaultTaskResult(TaskResult.Status status) {
    this(status, [:])
  }

  DefaultTaskResult(TaskResult.Status status, Map<String, ? extends Object> outputs) {
    this.status = status
    this.outputs = ImmutableMap.copyOf(outputs)
  }

}

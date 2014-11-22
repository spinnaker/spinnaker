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

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonBackReference
import com.netflix.spinnaker.orca.ExecutionStatus
import groovy.transform.CompileStatic

@CompileStatic
interface Stage<T extends Execution> {
  /**
   * The type as it corresponds to the Mayo configuration
   */
  String getType()

  /**
   * Gets the execution object for this stage
   */
  T getExecution()

  /**
   * The execution status for this stage
   */
  ExecutionStatus getStatus()

  /**
   * sets the execution status for this stage
   */
  void setStatus(ExecutionStatus status)

  /**
   * Gets the last stage preceding this stage that has the specified type.
   */
  Stage preceding(String type)

  Map<String, Object> getContext()

  ImmutableStage asImmutable()
}

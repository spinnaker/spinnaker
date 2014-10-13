/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.TaskResult

/**
 * A _stage_ of an Orca _pipeline_.
 */
interface Stage {

  /**
   * @return the name that corresponds to Mayo config.
   */
  String getName()

  /**
   * @return the status of the stage. Effectively this will mean the status of
   * the last {@link com.netflix.spinnaker.orca.Task} to be executed.
   *
   * TODO: need to ensure this never returns SUCCEEDED if an interim task has
   * completed and the next one has yet to start – in that case it should return
   * RUNNING
   */
  TaskResult.Status getStatus()
}

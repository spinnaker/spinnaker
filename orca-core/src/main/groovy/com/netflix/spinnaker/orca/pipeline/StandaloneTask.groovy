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

import com.netflix.spinnaker.orca.Task

/**
 * Represents a `Task` that can be run on its own as a pipeline stage. Such tasks do _not_ need to have a
 * {@link StageBuilder} registered in the application context, they just need to be registered in the application
 * context themselves.
 *
 * Retryable standalone tasks should implement this interface _and_ {@link com.netflix.spinnaker.orca.RetryableTask}.
 */
interface StandaloneTask extends Task {

  /**
   * @return the name corresponding to the Mayo configuration for this task.
   */
  String getName()

}
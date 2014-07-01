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

package com.netflix.spinnaker.orca.workflow

import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.JobBuilderHelper

/**
 * An object that constructs steps for a job relating to a specific logical workflow.
 */
interface WorkflowBuilder<B extends JobBuilderHelper<B>> {

  /**
   * Implementations should construct any steps necessary for the workflow and append them to +jobBuilder+. This method
   * is typically called when the workflow is the first in the job.
   *
   * @param jobBuilder the builder for the job. Implementations should append steps to this.
   * @return the resulting builder after any steps are appended.
   */
  abstract B build(JobBuilder jobBuilder)

  /**
   * Implementations should construct any steps necessary for the workflow and append them to +jobBuilder+. This method
   * is typically called when the workflow is not the first in the job.
   *
   * @param jobBuilder the builder for the job. Implementations should append steps to this.
   * @return the resulting builder after any steps are appended.
   */
  abstract B build(B jobBuilder)
}
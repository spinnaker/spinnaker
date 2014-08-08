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

package com.netflix.spinnaker.orca.pipeline

import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.JobBuilderHelper

/**
 * An object that constructs steps for a Spring Batch +Job+ relating to a specific Orca _stage_.
 */
interface StageBuilder<B extends JobBuilderHelper<B>> {

  /**
   * @return the name that corresponds to Mayo config.
   */
  String getName()

  // TODO: may not need this method if we always have a config handling step first
  /**
   * Implementations should construct any steps necessary for the stage and append them to +jobBuilder+. This method
   * is typically called when the stage is the first in the pipeline.
   *
   * @param jobBuilder the builder for the job. Implementations should append steps to this.
   * @return the resulting builder after any steps are appended.
   */
  abstract B build(JobBuilder jobBuilder)

  /**
   * Implementations should construct any steps necessary for the stage and append them to +jobBuilder+. This method
   * is typically called when the stage is not the first in the pipeline.
   *
   * @param jobBuilder the builder for the job. Implementations should append steps to this.
   * @return the resulting builder after any steps are appended.
   */
  abstract B build(B jobBuilder)

}
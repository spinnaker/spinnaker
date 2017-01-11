/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.batch

import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.core.task.TaskExecutor

@CompileStatic
class MultiThreadedBatchConfigurer extends DefaultBatchConfigurer {

  private final TaskExecutor taskExecutor

  MultiThreadedBatchConfigurer(TaskExecutor taskExecutor) {
    this.taskExecutor = taskExecutor
  }

//  @Override
  // Not overridden until we upgrade spring batch to 3.0.4. Then we can get rid of the jobLauncher field, getter and overridden initialize method
  protected JobLauncher createJobLauncher() {
    def launcher = new SimpleJobLauncher()
    launcher.jobRepository = jobRepository
    launcher.taskExecutor = taskExecutor
    launcher.afterPropertiesSet()
    launcher
  }

  // Once Batch is upgraded to 3.0.4 cut here...
  private JobLauncher jobLauncher

  @Override
  JobLauncher getJobLauncher() {
    return jobLauncher
  }

  @Override
  void initialize() {
    super.initialize()
    this.jobLauncher = createJobLauncher()
  }
  // ... to here
}

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

package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.batch.RetryableTaskTaskletAdapter
import com.netflix.spinnaker.orca.kato.tasks.CreateDeployTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.pipeline.LinearStageBuilder
import org.springframework.batch.core.Step
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DeployStageBuilder extends LinearStageBuilder {

  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("CreateDeployStep")
        .tasklet(super.buildTask(CreateDeployTask))
        .build()
    def step2 = steps.get("MonitorDeployStep")
        .tasklet(buildTask(MonitorKatoTask))
        .build()
/*    def step3 = steps.get("WaitForUpInstancesStep")
        .tasklet(buildTask(WaitForUpInstancesTask))
        .build()*/
    [step1, step2]
  }

  protected Tasklet buildTask(Class<? extends Task> taskType) {
    def task = taskType.newInstance()
    autowire task
    RetryableTaskTaskletAdapter.decorate task
  }
}

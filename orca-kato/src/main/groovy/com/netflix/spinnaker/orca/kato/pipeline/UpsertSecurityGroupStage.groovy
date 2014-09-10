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

import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.TerminateInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertSecurityGroupTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForTerminatedInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForUpsertedSecurityGroupTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class UpsertSecurityGroupStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "upsertSecurityGroup"

  UpsertSecurityGroupStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("UpsertSecurityGroupStep")
      .tasklet(buildTask(UpsertSecurityGroupTask))
      .build()

    def step2 = steps.get("MonitorUpsertStep")
      .tasklet(buildTask(MonitorKatoTask))
      .build()

    def step3 = steps.get("WaitForUpsertedSecurityGroupStep")
      .tasklet(buildTask(WaitForUpsertedSecurityGroupTask))
      .build()

    [step1, step2, step3]
  }
}

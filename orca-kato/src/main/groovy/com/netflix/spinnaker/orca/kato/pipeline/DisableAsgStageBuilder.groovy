package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.kato.tasks.DisableAsgTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorTask
import com.netflix.spinnaker.orca.pipeline.LinearStageBuilder
import org.springframework.batch.core.Step

class DisableAsgStageBuilder extends LinearStageBuilder {
  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("DisableAsgStep")
        .tasklet(buildTask(DisableAsgTask))
        .build()
    def step2 = steps.get("MonitorAsgStep")
        .tasklet(buildTask(MonitorTask))
        .build()
    [step1, step2]
  }
}

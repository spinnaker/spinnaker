package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.kato.tasks.EnableAsgTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorTask
import com.netflix.spinnaker.orca.pipeline.LinearStageBuilder
import org.springframework.batch.core.Step


class EnableAsgStageBuilder extends LinearStageBuilder {
  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("EnableAsgStep")
        .tasklet(buildTask(EnableAsgTask))
        .build()
    def step2 = steps.get("MonitorAsgStep")
        .tasklet(buildTask(MonitorTask))
        .build()
    [step1, step2]
  }
}

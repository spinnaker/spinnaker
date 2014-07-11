package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.DisableAsgTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.pipeline.LinearStageBuilder
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DisableAsgStageBuilder extends LinearStageBuilder {
  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("DisableAsgStep")
        .tasklet(buildTask(DisableAsgTask))
        .build()
    def step2 = steps.get("MonitorAsgStep")
        .tasklet(buildTask(MonitorKatoTask))
        .build()
    [step1, step2]
  }

}

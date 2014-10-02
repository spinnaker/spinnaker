


package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.kato.tasks.AmazonLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.NotifyEchoTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertAmazonLoadBalancerTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class UpsertAmazonLoadBalancerStage extends LinearStage {

  public static final String MAYO_CONFIG_TYPE = "upsertAmazonLoadBalancer"

  UpsertAmazonLoadBalancerStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("UpsertAmazonLoadBalancerStep")
      .tasklet(buildTask(UpsertAmazonLoadBalancerTask))
      .build()

    def step2 = steps.get("MonitorUpsertStep")
      .tasklet(buildTask(MonitorKatoTask))
      .build()

    def step3 = steps.get("ForceCacheRefreshStep")
      .tasklet(buildTask(AmazonLoadBalancerForceRefreshTask))
      .build()

    def step4 = steps.get("NotifyEchoStep")
      .tasklet(buildTask(NotifyEchoTask))
      .build()

    [step1, step2, step3, step4]
  }
}

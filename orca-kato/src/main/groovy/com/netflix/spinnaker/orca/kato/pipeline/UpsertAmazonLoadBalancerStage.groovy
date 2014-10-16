


package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.NotifyEchoTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertAmazonLoadBalancerForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.UpsertAmazonLoadBalancerTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
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
    def step1 = buildStep("upsertAmazonLoadBalancer", UpsertAmazonLoadBalancerTask)
    def step2 = buildStep("monitorUpsert", MonitorKatoTask)
    def step3 = buildStep("forceCacheRefresh", UpsertAmazonLoadBalancerForceRefreshTask)
    def step4 = buildStep("sendNotification", NotifyEchoTask)
    [step1, step2, step3, step4]
  }
}

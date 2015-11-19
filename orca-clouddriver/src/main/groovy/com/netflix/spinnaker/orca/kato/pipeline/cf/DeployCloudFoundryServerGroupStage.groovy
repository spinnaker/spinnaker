package com.netflix.spinnaker.orca.kato.pipeline.cf
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.WaitForUpInstancesTask
import com.netflix.spinnaker.orca.kato.tasks.cf.CreateCloudFoundryDeployTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component
@Component
@CompileStatic
class DeployCloudFoundryServerGroupStage extends LinearStage {

  public static final String PIPELINE_CONFIG_TYPE = "linearDeploy_cf"

  DeployCloudFoundryServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    def steps = []

    steps << buildStep(stage, "createDeploy", CreateCloudFoundryDeployTask)
    steps << buildStep(stage, "monitorDeploy", MonitorKatoTask)
//    steps << buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    steps << buildStep(stage, "waitForUpInstances", WaitForUpInstancesTask)
//    steps << buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask)

    steps
  }

}

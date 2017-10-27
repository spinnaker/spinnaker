package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import static com.netflix.spinnaker.orca.mine.pipeline.CanaryStage.DEFAULT_CLUSTER_DISABLE_WAIT_TIME

@Component
@Slf4j
class DisableCanaryTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired MineService mineService
  @Autowired KatoService katoService

  @Override
  TaskResult execute(Stage stage) {

    Integer waitTime = stage.context.clusterDisableWaitTime != null ? stage.context.clusterDisableWaitTime : DEFAULT_CLUSTER_DISABLE_WAIT_TIME

    try {
      def canary = mineService.getCanary(stage.context.canary.id)
      if (canary.health?.health == 'UNHEALTHY' || stage.context.unhealthy != null) {
        // If unhealthy, already disabled in MonitorCanaryTask
        return new TaskResult(ExecutionStatus.SUCCEEDED, [
          waitTime  : waitTime,
          unhealthy : true
        ])
      }
    } catch (RetrofitError e) {
      log.error("Exception occurred while getting canary status with id {} from mine, continuing with disable",
        stage.context.canary.id, e)
    }

    def selector = stage.context.containsKey('disabledCluster') ? 'baselineCluster' : 'canaryCluster'
    def ops = DeployedClustersUtil.toKatoAsgOperations('disableServerGroup', stage.context, selector)
    def dSG = DeployedClustersUtil.getDeployServerGroups(stage.context)

    log.info "Disabling ${selector} in ${stage.id} with ${ops}"
    String cloudProvider = ops && !ops.empty ? ops.first()?.values().first()?.cloudProvider : getCloudProvider(stage) ?: 'aws'
    def taskId = katoService.requestOperations(cloudProvider, ops).toBlocking().first()

    stage.context.remove('waitTaskState')
    return new TaskResult(ExecutionStatus.SUCCEEDED, [
      'kato.last.task.id'    : taskId,
      'deploy.server.groups' : dSG,
      disabledCluster        : selector,
      waitTime               : waitTime
    ])
  }
}

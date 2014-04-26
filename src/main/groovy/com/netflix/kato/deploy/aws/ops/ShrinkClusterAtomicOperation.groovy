package com.netflix.kato.deploy.aws.ops

import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest
import com.netflix.frigga.Names
import com.netflix.kato.data.task.InMemoryTaskRepository
import com.netflix.kato.data.task.Task
import com.netflix.kato.deploy.aws.description.ShrinkClusterDescription
import com.netflix.kato.orchestration.AtomicOperation
import org.springframework.web.client.RestTemplate

import static com.netflix.kato.deploy.aws.StaticAmazonClients.getAutoScaling

class ShrinkClusterAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    InMemoryTaskRepository.localTask.get()
  }

  final ShrinkClusterDescription description
  final RestTemplate rt = new RestTemplate()

  ShrinkClusterAtomicOperation(ShrinkClusterDescription description) {
    this.description = description
  }

  @Override
  Void operate(List _) {
    task.updateStatus BASE_PHASE, "Initializing Cluster Shrinking Operation..."
    for (String region in description.regions) {
      def autoScaling = getAutoScaling(description.credentials.accessId, description.credentials.secretKey, region)

      task.updateStatus BASE_PHASE, "Looking up inactive ASGs in ${region}..."
      List<String> inactiveAsgs = getInactiveAsgs(region)
      for (String inactiveAsg : inactiveAsgs) {
        task.updateStatus BASE_PHASE, "Removing ASG -> ${inactiveAsg}"
        try {
          def request = new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(inactiveAsg)
              .withForceDelete(description.forceDelete)
          autoScaling.deleteAutoScalingGroup(request)
          task.updateStatus BASE_PHASE, "Deleted ASG -> ${inactiveAsg}"
        } catch (IGNORE) {}
      }
    }
    task.updateStatus BASE_PHASE, "Finished Shrinking Cluster."
  }

  List<String> getInactiveAsgs(String region) {
    def env = description.credentials.environment

    List<String> asgs = rt.getForEntity("http://entrypoints-v2.${region}.${env}.netflix.net:7001/REST/v2/aws/autoScalingGroups", List).body
    def appAsgs = asgs.findAll {
      def names = Names.parseName(it)
      description.clusterName == names.cluster && description.application == names.app
    }
    appAsgs.findAll { String asgName ->
      try {
        Map asg = rt.getForEntity("http://entrypoints-v2.${region}.${env}.netflix.net:7001/REST/v2/aws/autoScalingGroups/$asgName", Map).body
        !asg.instances
      } catch (IGNORE) {}
    }
  }
}

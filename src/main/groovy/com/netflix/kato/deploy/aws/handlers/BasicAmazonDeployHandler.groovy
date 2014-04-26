package com.netflix.kato.deploy.aws.handlers

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.*
import com.netflix.kato.deploy.aws.AutoScalingWorker
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import groovy.util.logging.Log4j
import org.springframework.stereotype.Component


import static com.netflix.kato.deploy.aws.StaticAmazonClients.getAmazonEC2
import static com.netflix.kato.deploy.aws.StaticAmazonClients.getAutoScaling

@Log4j
@Component
class BasicAmazonDeployHandler implements DeployHandler<BasicAmazonDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicAmazonDeployDescription
  }

  @Override
  DeploymentResult handle(BasicAmazonDeployDescription description) {
    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()
    task.updateStatus BASE_PHASE, "Preparing deployment to ${description.availabilityZones}..."
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key
      List<String> availabilityZones = entry.value

      def amazonEC2 = getAmazonEC2(description.credentials.accessId, description.credentials.secretKey, region)
      def autoScaling = getAutoScaling(description.credentials.accessId, description.credentials.secretKey, region)
      def autoScalingWorker = new AutoScalingWorker(description.application, region, description.credentials.environment,
          description.clusterName, description.amiName, description.capacity.min, description.capacity.max,
          description.capacity.desired, description.instanceType, availabilityZones, amazonEC2, autoScaling)
      def asgName = autoScalingWorker.deploy()

      deploymentResult.serverGroupNames << "${region}:${asgName}"
    }

    deploymentResult
  }

}

package com.netflix.kato.deploy.aws.handlers

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.*
import com.netflix.kato.deploy.aws.AutoScalingWorker
import com.netflix.kato.deploy.aws.description.BasicAmazonDeployDescription
import com.netflix.kato.deploy.aws.ops.loadbalancer.CreateLoadBalancerResult
import com.netflix.kato.deploy.aws.userdata.UserDataProvider
import groovy.util.logging.Log4j
import org.springframework.beans.factory.annotation.Autowired
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

  @Autowired
  List<UserDataProvider> userDataProviders

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicAmazonDeployDescription
  }

  @Override
  DeploymentResult handle(BasicAmazonDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing handler..."
    def deploymentResult = new DeploymentResult()
    task.updateStatus BASE_PHASE, "Preparing deployment to ${description.availabilityZones}..."
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      String region = entry.key
      List<String> availabilityZones = entry.value

      // Get the list of load balancers that were created as part of this conglomerate job to apply to the ASG.
      List<CreateLoadBalancerResult.LoadBalancer> suppliedLoadBalancers = (List<CreateLoadBalancerResult.LoadBalancer>)priorOutputs.findAll {
        it instanceof CreateLoadBalancerResult }?.loadBalancers?.getAt(region)

      def amazonEC2 = getAmazonEC2(description.credentials, region)
      def autoScaling = getAutoScaling(description.credentials, region)

      def autoScalingWorker = new AutoScalingWorker(
          application: description.application,
          region: region,
          environment: description.credentials.environment,
          stack: description.stack,
          ami: description.amiName,
          minInstances: description.capacity.min,
          maxInstances: description.capacity.max,
          desiredInstances: description.capacity.desired,
          securityGroups: description.securityGroups,
          instanceType: description.instanceType,
          availabilityZones: availabilityZones,
          amazonEC2: amazonEC2,
          autoScaling: autoScaling,
          loadBalancers: suppliedLoadBalancers?.name,
          userDataProviders: userDataProviders
      )

      def asgName = autoScalingWorker.deploy()

      deploymentResult.serverGroupNames << "${region}:${asgName}"
    }

    deploymentResult
  }
}

package com.netflix.kato.deploy.aws.ops.loadbalancer

import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Listener
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.description.CreateLoadBalancerDescription
import com.netflix.kato.orchestration.AtomicOperation


import static com.netflix.kato.deploy.aws.StaticAmazonClients.getAmazonElasticLoadBalancing

class CreateLoadBalancerAtomicOperation implements AtomicOperation<CreateLoadBalancerResult> {
  private static final String BASE_PHASE = "CREATE_ELB"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateLoadBalancerDescription description

  CreateLoadBalancerAtomicOperation(CreateLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  CreateLoadBalancerResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing load balancer creation..."

    def operationResult = new CreateLoadBalancerResult(loadBalancers: [:])
    for (Map.Entry<String, List<String>> entry : description.availabilityZones) {
      def region = entry.key
      def availabilityZones = entry.value
      def loadBalancerName = "${description.clusterName}-frontend"

      task.updateStatus BASE_PHASE, "Beginning deployment to $region in $availabilityZones for $loadBalancerName"

      def request = new CreateLoadBalancerRequest(loadBalancerName)
      request.withAvailabilityZones(availabilityZones)

      def listeners = []
      for (CreateLoadBalancerDescription.Listener listener : description.listeners) {
        def awsListener = new Listener()
        awsListener.withLoadBalancerPort(listener.externalPort).withInstancePort(listener.internalPort)

        awsListener.withProtocol(listener.externalProtocol.name())
        if (listener.internalProtocol && (listener.externalProtocol != listener.internalProtocol)) {
          awsListener.withInstanceProtocol(listener.internalProtocol.name())
        } else {
          awsListener.withInstanceProtocol(listener.externalProtocol.name())
        }
        listeners << awsListener
        task.updateStatus BASE_PHASE, " > Appending listener ${awsListener.protocol}:${awsListener.loadBalancerPort} -> ${awsListener.instanceProtocol}:${awsListener.instancePort}"
      }
      request.withListeners(listeners)

      def client = getAmazonElasticLoadBalancing(description.credentials, region)
      task.updateStatus BASE_PHASE, "Deploying ${loadBalancerName} to ${description.credentials.environment} in ${region}..."
      def result = client.createLoadBalancer(request)
      task.updateStatus BASE_PHASE, "Done deploying ${loadBalancerName} to ${description.credentials.environment} in ${region}."
      operationResult.loadBalancers[region] = new CreateLoadBalancerResult.LoadBalancer(loadBalancerName, result.DNSName)
    }
    task.updateStatus BASE_PHASE, "Done deploying load balancers."
    operationResult
  }
}

package com.netflix.spinnaker.kato.deploy.gce.handlers

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.AccessConfig
import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.Instance
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.deploy.gce.description.BasicGoogleDeployDescription
import org.springframework.stereotype.Component

@Component
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicGoogleDeployDescription
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "front50", "stack": "dev", "image": "debian-7-wheezy-v20140415", "type": "f1-micro", "zone": "us-central1-b", "credentials": "gce-test" }} ]' localhost:8501/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deployment..."

    def compute = description.credentials.compute
    def project = description.credentials.project

    task.updateStatus BASE_PHASE, "Looking up machine type..."
    def machineType = compute.machineTypes().list(project, "us-central1-b").execute().getItems().find { it.getName() == description.type }

    task.updateStatus BASE_PHASE, "Looking up Source Image..."
    def sourceImage = compute.images().list("debian-cloud").execute().getItems().find { it.getName() == description.image }

    task.updateStatus BASE_PHASE, "Looking up default network..."
    def networking = compute.networks().list(project).execute().getItems().find { it.getName() == "default" }

    task.updateStatus BASE_PHASE, "Composing instance..."
    def rootDrive = new AttachedDisk(boot: true, autoDelete: true, type: "PERSISTENT",
      initializeParams: new AttachedDiskInitializeParams(sourceImage: sourceImage.getSelfLink()))

    def network = new com.google.api.services.compute.model.NetworkInterface(network: networking.getSelfLink(),
      accessConfigs: [new AccessConfig(type: "ONE_TO_ONE_NAT")])

    def clusterName = "${description.application}-${description.stack}"
    task.updateStatus BASE_PHASE, "Looking up next sequence..."
    def nextSequence = getNextSequence(clusterName, project, description.zone, compute)
    task.updateStatus BASE_PHASE, "Found next sequence ${nextSequence}."
    def instanceName = "${clusterName}-v${nextSequence}-instance1".toString()
    task.updateStatus BASE_PHASE, "Produced instance name: $instanceName"

    def instance = new Instance(name: instanceName, machineType: machineType.getSelfLink(), disks: [rootDrive], networkInterfaces: [network])

    task.updateStatus BASE_PHASE, "Creating instance $instanceName..."
    compute.instances().insert(project, description.zone, instance).execute()
    task.updateStatus BASE_PHASE, "Done."
    new DeploymentResult(serverGroupNames: ["${clusterName}-v${nextSequence}".toString()])
  }

  static def getNextSequence(String clusterName, String project, String zone, Compute compute) {
    def instance = compute.instances().list(project, zone).execute().getItems().find {
      def parts = it.getName().split('-')
      def cluster = "${parts[0]}-${parts[1]}"
      cluster == clusterName
    }
    if (instance) {
      def parts = instance.getName().split('-')
      def seq = Integer.valueOf(parts[2].replaceAll("v", ""))
      String.format("%03d", ++seq)
    } else {
      "000"
    }
  }
}

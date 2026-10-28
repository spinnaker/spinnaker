package com.netflix.spinnaker.clouddriver.google.deploy

import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.WorkloadIdentityConfig
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import spock.lang.Specification

class WorkloadIdentitySpec extends Specification {

  void "buildInstanceDescriptionFromTemplate should extract workloadIdentityConfig"() {
    setup:
    def template = new InstanceTemplate(
      name: "test-template",
      properties: new InstanceProperties(
        machineType: "n1-standard-1",
        networkInterfaces: [new com.google.api.services.compute.model.NetworkInterface(network: "default")],
        disks: [new com.google.api.services.compute.model.AttachedDisk(boot: true, initializeParams: new com.google.api.services.compute.model.AttachedDiskInitializeParams(sourceImage: "projects/debian-cloud/global/images/debian-11"))],
        workloadIdentityConfig: new WorkloadIdentityConfig(
          identity: "test-identity",
          identityCertificateEnabled: true
        )
      )
    )

    when:
    def description = GCEUtil.buildInstanceDescriptionFromTemplate("test-project", template)

    then:
    description.workloadIdentityConfig != null
    description.workloadIdentityConfig.identity == "test-identity"
    description.workloadIdentityConfig.identityCertificateEnabled == true
  }

  void "buildInstanceDescriptionFromTemplate should handle null workloadIdentityConfig"() {
    setup:
    def template = new InstanceTemplate(
      name: "test-template",
      properties: new InstanceProperties(
        machineType: "n1-standard-1",
        networkInterfaces: [new com.google.api.services.compute.model.NetworkInterface(network: "default")],
        disks: [new com.google.api.services.compute.model.AttachedDisk(boot: true, initializeParams: new com.google.api.services.compute.model.AttachedDiskInitializeParams(sourceImage: "projects/debian-cloud/global/images/debian-11"))],
        workloadIdentityConfig: null
      )
    )

    when:
    def description = GCEUtil.buildInstanceDescriptionFromTemplate("test-project", template)

    then:
    description.workloadIdentityConfig == null
  }
}

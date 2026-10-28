/*
 * Copyright 2026 Google, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy

import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.NetworkInterface
import com.google.api.services.compute.model.WorkloadIdentityConfig
import spock.lang.Specification

class WorkloadIdentitySpec extends Specification {

  private static final String SOURCE_IMAGE = "projects/debian-cloud/global/images/debian-11"

  void "buildInstanceDescriptionFromTemplate should extract workloadIdentityConfig"() {
    setup:
    def template = buildTemplate(new WorkloadIdentityConfig(
      identity: "test-identity",
      identityCertificateEnabled: true
    ))

    when:
    def description = GCEUtil.buildInstanceDescriptionFromTemplate("test-project", template)

    then:
    description.workloadIdentityConfig != null
    description.workloadIdentityConfig.identity == "test-identity"
    description.workloadIdentityConfig.identityCertificateEnabled == true
  }

  void "buildInstanceDescriptionFromTemplate should handle null workloadIdentityConfig"() {
    setup:
    def template = buildTemplate(null)

    when:
    def description = GCEUtil.buildInstanceDescriptionFromTemplate("test-project", template)

    then:
    description.workloadIdentityConfig == null
  }

  private static InstanceTemplate buildTemplate(WorkloadIdentityConfig workloadIdentityConfig) {
    new InstanceTemplate(
      name: "test-template",
      properties: new InstanceProperties(
        machineType: "n1-standard-1",
        networkInterfaces: [new NetworkInterface(network: "default")],
        disks: [new AttachedDisk(
          boot: true,
          initializeParams: new AttachedDiskInitializeParams(sourceImage: SOURCE_IMAGE))],
        workloadIdentityConfig: workloadIdentityConfig
      )
    )
  }
}

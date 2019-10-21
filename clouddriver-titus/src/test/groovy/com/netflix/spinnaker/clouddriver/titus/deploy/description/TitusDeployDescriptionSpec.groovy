/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.titus.deploy.description

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.orchestration.SagaContextAware
import com.netflix.spinnaker.clouddriver.titus.client.model.MigrationPolicy
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import spock.lang.Specification
import spock.lang.Unroll

class TitusDeployDescriptionSpec extends Specification {

  @Unroll
  def "ser/de"() {

    Map signedAddressAllocations =  [
      addressAllocation : [
        addressLocation : [
          region          : "us-east-1",
          availabilityZone: "us-east-1d",
          subnetId        : "subnet-ffab009"
        ],
        uuid           : "7e571794-4a8b-4335-8be7-c5e3b2660688",
        address        : "192.122.100.100",
      ],
      authoritativePublicKey: "authoritativePublicKeyValue",
      hostPublicKey         : "hostPublicKeyValue",
      hostPublicKeySignature: "hostPublicKeySignatureValue",
      message               : "message",
      messageSignature      : "messageSignatureValue"
    ]
    given:
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()

    and:
    TitusDeployDescription subject = new TitusDeployDescription(
      account: "titustest",
      region: "us-east-1",
      application: "helloworld",
      capacity: new TitusDeployDescription.Capacity(
        desired: 1,
        max: 1,
        min: 1
      ),
      capacityGroup: "helloworld",
      containerAttributes: [:],
      credentials: credentials,
      env: [:],
      hardConstraints: [],
      iamProfile: "helloworldInstanceProfile",
      imageId: "titus/helloworld:latest",
      inService: true,
      labels: [:],
      migrationPolicy: new MigrationPolicy(
        type: "systemDefault"
      ),
      resources: new TitusDeployDescription.Resources(
        allocateIpAddress: true,
        cpu: 2,
        disk: 10000,
        memory: 4096,
        networkMbps: 128,
        signedAddressAllocations: [signedAddressAllocations]
      ),
      securityGroups: [],
      softConstraints: [],
      sagaContext: new SagaContextAware.SagaContext(
        "titus",
        "createServerGroup",
        [:]
      )
    )

    when:
    objectMapper.readValue(objectMapper.writeValueAsString(subject), TitusDeployDescription)

    then:
    noExceptionThrown()

    where:
    credentials << [
      null,
      Mock(NetflixTitusCredentials) {
        getName() >> "titustest"
      }
    ]
  }
}

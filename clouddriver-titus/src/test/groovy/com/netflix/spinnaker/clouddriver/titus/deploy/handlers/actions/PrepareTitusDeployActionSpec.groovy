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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers.actions

import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.saga.models.Saga
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.MigrationPolicy
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.LoadFront50App
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy.PrepareTitusDeployCommand
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.config.AwsConfiguration
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PrepareTitusDeployActionSpec extends Specification {

  NetflixTitusCredentials netflixTitusCredentials = new NetflixTitusCredentials(
    'test', 'test', 'test', [new TitusRegion('us-east-1', 'test', 'http://foo', false, false, "blah", "blah", 7104, [])], 'test', 'test', 'test', 'test', false, '', 'mainvpc', [], "", false, false, false
  )

  AccountCredentialsRepository accountCredentialsRepository = Mock() {
    getOne("test") >> {
      return netflixTitusCredentials
    }
  }
  TitusClient titusClient = Mock(TitusClient)
  TitusClientProvider titusClientProvider = Mock() {
    getTitusClient(_, _) >> titusClient
  }
  AwsLookupUtil awsLookupUtil = Mock()
  RegionScopedProviderFactory regionScopedProviderFactory = Mock()
  AccountCredentialsProvider accountCredentialsProvider = Mock()
  AwsConfiguration.DeployDefaults deployDefaults = Mock()

  Saga saga = new Saga("titusDeploy", "id")

  @Subject
  PrepareTitusDeploy subject = new PrepareTitusDeploy(
    accountCredentialsRepository,
    titusClientProvider,
    awsLookupUtil,
    regionScopedProviderFactory,
    accountCredentialsProvider,
    deployDefaults,
    Optional.empty()
  )

  def "merges source details when no asg name is provided"() {
    given:
    TitusDeployDescription description = createTitusDeployDescription()
    description.source = new TitusDeployDescription.Source(
      account: "test",
      region: "us-east-1",
      asgName: "spindemo-staging-highlander-v000",
      useSourceCapacity: true
    )

    and:
    PrepareTitusDeployCommand command = createCommand(description)

    when:
    def result = subject.apply(command, saga)

    then:
    titusClient.findJobByName(_) >> {
      new Job(
        applicationName: "spindemo",
        digest: "abcd",
        securityGroups: ["hello"],
        instancesMin: 10,
        instancesMax: 10,
        instancesDesired: 10,
        labels: [sourceLabel: "sourceVal"],
        environment: ["HI": "hello"],
        containerAttributes: [sourceAttr: "sourceVal"],
        softConstraints: [],
        hardConstraints: [],
        serviceJobProcesses: [
          disableIncreaseDesired: true,
          disableDecreaseDesired: true
        ]
      )
    }
    awsLookupUtil.securityGroupIdExists(_, _, _) >> true

    result.events.isEmpty() == true
    result.nextCommand instanceof SubmitTitusJob.SubmitTitusJobCommand
    result.nextCommand.description.with {
      securityGroups == ["hello"]
      capacity.min == 10
      capacity.max == 10
      capacity.desired == 10
      labels == [sourceLabel: "sourceVal"]
      containerAttributes == [sourceAttr: "sourceVal"]
      env == ["HI": "hello"]
      serviceJobProcesses == [
        disableIncreaseDesired: true,
        disableDecreaseDesired: true
      ]
    }
  }

  def "security groups are resolved"() {
    given:
    TitusDeployDescription description = createTitusDeployDescription()
    description.securityGroups = ["sg-1", "fancyname"]

    when:
    subject.resolveSecurityGroups(saga, description)

    then:
    awsLookupUtil.securityGroupIdExists(_, _, "sg-1") >> true
    awsLookupUtil.securityGroupIdExists(_, _, "fancyname") >> false
    awsLookupUtil.convertSecurityGroupNameToId(_, _, "fancyname") >> "sg-2"

    description.securityGroups == ["sg-2", "sg-1"]
  }

  @Unroll
  def "security groups include app security group (label=#labelValue, desc=#descriptionValue, includesAppGroup=#includesAppGroup)"() {
    given:
    TitusDeployDescription description = createTitusDeployDescription()

    and:
    if (labelValue != null) {
      description.labels[PrepareTitusDeploy.USE_APPLICATION_DEFAULT_SG_LABEL] = labelValue.toString()
    }
    if (descriptionValue != null) {
      description.useApplicationDefaultSecurityGroup = descriptionValue
    }

    when:
    subject.resolveSecurityGroups(saga, description)

    then:
    awsLookupUtil.securityGroupIdExists(_, _, "sg-abcd1234") >> true
    awsLookupUtil.convertSecurityGroupNameToId(_, _, "spindemo") >> "sg-spindemo"

    if (includesAppGroup) {
      description.securityGroups == ["sg-abcd1234", "sg-spindemo"]
    } else {
      description.securityGroups == ["sg-abcd1234"]
    }

    where:
    labelValue | descriptionValue || includesAppGroup
    null       | null             || true
    true       | null             || true
    false      | null             || false
    true       | true             || true
    true       | false            || true
    null       | true             || true
    null       | false            || false
  }

  private TitusDeployDescription createTitusDeployDescription() {
    return new TitusDeployDescription(
      application: "spindemo",
      capacity: new TitusDeployDescription.Capacity(
        desired: 1,
        max: 1,
        min: 1
      ),
      capacityGroup: "spindemo",
      containerAttributes: [:] as Map<String, String>,
      credentials: netflixTitusCredentials,
      env: [:] as Map<String, String>,
      freeFormDetails: "highlander",
      hardConstraints: [
        "UniqueHost",
        "ZoneBalance"
      ],
      iamProfile: "spindemoInstanceProfile",
      imageId: "spinnaker/basic:master-h47400.3aa8911",
      inService: true,
      labels: [:] as Map<String, String>,
      migrationPolicy: new MigrationPolicy(type: "systemDefault"),
      region: "us-east-1",
      resources: new TitusDeployDescription.Resources(
        allocateIpAddress: true,
        cpu: 1,
        disk: 5_000,
        gpu: 0,
        memory: 5_000,
        networkMbps: 128
      ),
      securityGroups: [
        "sg-abcd1234"
      ],
      softConstraints: [],
      stack: "staging",
    )
  }

  private static PrepareTitusDeployCommand createCommand(TitusDeployDescription description) {
    return createCommand(description, null, false)
  }

  private static PrepareTitusDeployCommand createCommand(
    TitusDeployDescription description, String email, boolean platformHealthOnly) {
    return new PrepareTitusDeployCommand(description).with {
      it.front50App = new LoadFront50App.Front50App(email, platformHealthOnly)
      it
    }
  }
}

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
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy.PrepareTitusDeployCommand
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.kork.test.mimicker.DataContainer
import com.netflix.spinnaker.kork.test.mimicker.Mimicker
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.moniker.frigga.FriggaReflectiveNamer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PrepareTitusDeployActionSpec extends Specification {

  @Shared
  Mimicker mimicker = new Mimicker(new DataContainer(["mimicker-titus.yml"]).withDefaultResources())

  Fixture fixture = new Fixture(mimicker)

  NetflixTitusCredentials netflixTitusCredentials = new NetflixTitusCredentials(
    fixture.accountName,
    mimicker.text().word(),
    mimicker.text().word(),
    [
      new TitusRegion(
        mimicker.aws().getAvailabilityZone(fixture.region),
        fixture.accountName,
        'http://region', // TODO(rz): mimicker.network().url
        mimicker.random().trueOrFalse(),
        mimicker.random().trueOrFalse(),
        fixture.moniker.app,
        mimicker.text().word(),
        mimicker.network().port,
        [],
        null,
        null
      )
    ],
    'http://bastion', // TODO(rz): mimicker.network().url
    mimicker.text().word(),
    mimicker.text().word(),
    mimicker.text().word(),
    mimicker.random().trueOrFalse(),
    mimicker.text().word(),
    mimicker.text().word(),
    [],
    Permissions.EMPTY,
    mimicker.text().word(),
    mimicker.random().trueOrFalse(),
    mimicker.random().trueOrFalse(),
    mimicker.random().trueOrFalse()
  )

  AccountCredentialsRepository accountCredentialsRepository = Mock() {
    getOne(fixture.accountName) >> {
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

  Saga saga = new Saga(mimicker.random().uuid(), mimicker.random().uuid())

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
    TitusDeployDescription description = createTitusDeployDescription(mimicker.aws().securityGroupId)
    description.source = new TitusDeployDescription.Source(
      account: fixture.accountName,
      region: fixture.region,
      asgName: fixture.monikerName,
      useSourceCapacity: mimicker.random().trueOrFalse()
    )

    and:
    PrepareTitusDeployCommand command = createCommand(description)

    when:
    def result = subject.apply(command, saga)

    then:
    titusClient.findJobByName(_) >> {
      new Job(
        applicationName: fixture.moniker.app,
        digest: mimicker.text().word(),
        securityGroups: [mimicker.text().word()],
        instancesMin: instancesMin,
        instancesMax: instancesMax,
        instancesDesired: instancesDesired,
        labels: [passThru: "label value"],
        environment: [passThru: "environment value"],
        containerAttributes: [passThru: "containerAttributes value"],
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
      capacity.min == instancesMin
      capacity.max == instancesMax
      capacity.desired == instancesDesired
      labels == [passThru: "label value"]
      env == [passThru: "environment value"]
      containerAttributes == [passThru: "containerAttributes value"]
      serviceJobProcesses == [
        disableIncreaseDesired: true,
        disableDecreaseDesired: true
      ]
    }

    where:
    instancesMin = mimicker.random().intValue(0, 100_000)
    instancesMax = mimicker.random().intValue(0, 100_000)
    instancesDesired = mimicker.random().intValue(0, 100_000)
  }

  def "security groups are resolved"() {
    given:
    TitusDeployDescription description = createTitusDeployDescription(sg1Id)
    description.securityGroups = [sg1Id, sg2Name]

    when:
    subject.resolveSecurityGroups(saga, description)

    then:
    awsLookupUtil.securityGroupIdExists(_, _, sg1Id) >> true
    awsLookupUtil.securityGroupIdExists(_, _, sg2Name) >> false
    awsLookupUtil.convertSecurityGroupNameToId(_, _, sg2Name) >> sg2Id

    description.securityGroups.sort() == [sg2Id, sg1Id].sort()

    where:
    sg1Id = mimicker.aws().securityGroupId
    sg2Name = mimicker.text().word()
    sg2Id = mimicker.aws().securityGroupId
  }

  @Unroll
  def "security groups include app security group (label=#labelValue, desc=#descriptionValue, includesAppGroup=#includesAppGroup)"() {
    given:
    TitusDeployDescription description = createTitusDeployDescription(sg1Id)

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
    awsLookupUtil.securityGroupIdExists(_, _, sg1Id) >> true
    awsLookupUtil.convertSecurityGroupNameToId(_, _, sg2Name) >> sg2Id

    if (includesAppGroup) {
      description.securityGroups == [sg1Id, sg2Id]
    } else {
      description.securityGroups == [sg1Id]
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

    sg1Id = mimicker.aws().securityGroupId
    sg2Name = mimicker.text().word()
    sg2Id = mimicker.aws().securityGroupId
  }

  private TitusDeployDescription createTitusDeployDescription(String securityGroupId) {
    return new TitusDeployDescription(
      application: fixture.moniker.app,
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
      region: fixture.region,
      resources: new TitusDeployDescription.Resources(
        allocateIpAddress: true,
        cpu: 1,
        disk: 5_000,
        gpu: 0,
        memory: 5_000,
        networkMbps: 128
      ),
      securityGroups: [
        securityGroupId
      ],
      softConstraints: [],
      stack: "staging",
    )
  }

  private static class Fixture {
    Moniker moniker = mimicker.moniker().get()
    // TODO(rz): barf
    String monikerName
    String region
    String accountName

    Fixture(Mimicker mimicker) {
      moniker = mimicker.moniker().get()
      region = mimicker.aws().region
      accountName = mimicker.text().word()
      new FriggaReflectiveNamer().applyMoniker(this, moniker)
    }

    String setName(String name) {
      monikerName = name
    }
  }

  private static PrepareTitusDeployCommand createCommand(TitusDeployDescription description) {
    return createCommand(description, null, false)
  }

  private static PrepareTitusDeployCommand createCommand(
    TitusDeployDescription description, String email, boolean platformHealthOnly) {
    return PrepareTitusDeployCommand.builder()
      .description(description)
      .front50App(new LoadFront50App.Front50App(email, platformHealthOnly))
      .build()
  }
}

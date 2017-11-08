/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.AmazonServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.dcos.DcosServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce.GoogleServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes.KubernetesServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.titus.TitusServerGroupCreator
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DetermineHealthProvidersTaskSpec extends Specification {
  def front50Service = Mock(Front50Service)

  @Subject
  def task = new DetermineHealthProvidersTask(
    new Optional<Front50Service>(front50Service),
    [],
    [new KubernetesServerGroupCreator(), new AmazonServerGroupCreator(), new GoogleServerGroupCreator(), new TitusServerGroupCreator(), new DcosServerGroupCreator()]
  )

  @Unroll
  def "should set interestingHealthProviderNames based on application config"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "", stageContext)

    if (application) {
      1 * front50Service.get(application.name) >> application
    }

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
    (taskResult.getContext() as Map) == expectedStageOutputs

    where:
    stageContext                                  | application            || expectedStageOutputs
    [interestingHealthProviderNames: []]          | null                   || [:]
    [interestingHealthProviderNames: null]        | null                   || [:]
    [application: "app"]                          | bA("app", true, false) || [interestingHealthProviderNames: ["Amazon"]]
    [application: "app", cloudProvider: "gce"]    | bA("app", true, false) || [interestingHealthProviderNames: ["Google"]]
    [application: "app"]                          | bA("app", true, true)  || [:]                                           // no health provider names when platformHealthOnlyShowOverride is true
    [application: "app", cloudProvider: "random"] | null                   || [:]                                           // no health provider names when cloud provider is unsupported/unknown
    [application: "app"]                          | null                   || [:]                                           // no health provider names when an exception is raised
    [moniker: [app: "app"]]                       | null                   || [:]
  }

  private static bA(String applicationName, Boolean platformHealthOnly, Boolean platformHealthOnlyShowOverride) {
    return new Application(
      name: applicationName,
      platformHealthOnly: platformHealthOnly,
      platformHealthOnlyShowOverride: platformHealthOnlyShowOverride
    )
  }
}

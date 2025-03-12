/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job

import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageParameter
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.clouddriver.config.KubernetesPreconfiguredJobProperties
import com.netflix.spinnaker.orca.clouddriver.tasks.job.DestroyJobTask
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Container
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class PreconfiguredJobStageSpec extends Specification {

  def "should replace properties in context"() {
    given:
    def jobService = Mock(JobService) {
      1 * getPreconfiguredStages() >> {
        return [
          preconfiguredJobProperties
        ]
      }
    }

    def stage = stage {
      type = stageName
      context = stageContext
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    noExceptionThrown()
    stage.getContext().get(expectedField) == expectedValue

    where:
    expectedField   | expectedValue   | stageName | stageContext                                                              | preconfiguredJobProperties
    "cloudProvider" | "kubernetes"    | "testJob" | [account: "test-account"]                                                 | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [], cloudProvider: "kubernetes")
    "cloudProvider" | "titus"         | "testJob" | [account: "test-account"]                                                 | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [new PreconfiguredJobStageParameter(mapping: "cloudProvider", defaultValue: "titus")], cloudProvider: "kubernetes")
    "cloudProvider" | "somethingElse" | "testJob" | [account: "test-account", parameters: ["cloudProvider": "somethingElse"]] | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [new PreconfiguredJobStageParameter(mapping: "cloudProvider", defaultValue: "titus", "name": "cloudProvider")], cloudProvider: "kubernetes")
    "cloudProvider" | "kubernetes"    | "testJob" | [account: "test-account", parameters: ["cloudProvider": "somethingElse"]] | new KubernetesPreconfiguredJobProperties(enabled: true, label: "testJob", type: "testJob", parameters: [new PreconfiguredJobStageParameter(defaultValue: "titus", "name": "cloudProvider")], cloudProvider: "kubernetes")
  }

  def "should use copy of preconfigured job to populate context"() {

    given:
    def manifestMetadataName = "defaultName"
    def overriddenName = "fromParameter"
    def stage = stage {
      type = "test"
      context = [account: "test"]
    }
    def property = new KubernetesPreconfiguredJobProperties(
      enabled: true,
      label: "test",
      type: "test",
      cloudProvider: "kubernetes",
      parameters: [
        new PreconfiguredJobStageParameter(
          mapping: "manifest.metadata.name",
          defaultValue: "fromParameter",
          name: "metadataName"
        )
      ],
      manifest: new V1Job(metadata: [name: "defaultName"])
    )

    def jobService = Mock(JobService) {
      2 * getPreconfiguredStages() >> {
        return [
          property
        ]
      }
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    // verify that underlying job configuration hasn't been modified
    def preconfiguredJob = (KubernetesPreconfiguredJobProperties) jobService.getPreconfiguredStages().get(0)
    preconfiguredJob.getManifest().getMetadata().getName() == manifestMetadataName
    // verify that stage manifest has the correctly overridden name
    stage.getContext().get("manifest").metadata.name == overriddenName
  }

  def "setNestedValue can handle array property mappings"() {
    given:
    def manifestEnvValue = "defaultValue"
    def overriddenValue = "newValue"
    def stage = stage {
      type = "test"
      context = [account: "test", dynamicParameters: ["manifest.addValue": "value"]]
    }
    def property = new KubernetesPreconfiguredJobProperties(
      enabled: true,
      label: "test",
      type: "test",
      cloudProvider: "kubernetes",
      parameters: [
        new PreconfiguredJobStageParameter(
          mapping: "manifest.spec.template.spec.containers[0].env[0].value",
          defaultValue: overriddenValue,
          name: "envVariable"
        )
      ],
      manifest: new V1Job(spec: [template: [spec: [containers: [new V1Container(env: [new V1EnvVar(name: "foo", value: manifestEnvValue)])]]]])
    )

    def jobService = Mock(JobService) {
      2 * getPreconfiguredStages() >> {
        return [
          property
        ]
      }
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    // verify that underlying job configuration hasn't been modified
    def preconfiguredJob = (KubernetesPreconfiguredJobProperties) jobService.getPreconfiguredStages().get(0)
    preconfiguredJob.getManifest().getSpec().getTemplate().getSpec().getContainers()[0].getEnv()[0].getValue() == manifestEnvValue
    // verify that stage manifest has the correctly overridden name
    stage.getContext().get("manifest").spec.template.spec.containers[0].env[0].value == overriddenValue
    stage.getContext().get("manifest").addValue == "value"
  }

  def "setNestedValue throws an error if a dynamic parameter root"() {
    given:
    def manifestEnvValue = "defaultValue"
    def overriddenValue = "newValue"
    def stage = stage {
      type = "test"
      context = [account: "test"]
    }
    def property = new KubernetesPreconfiguredJobProperties(
      enabled: true,
      label: "test",
      type: "test",
      cloudProvider: "kubernetes",
      parameters: [
        new PreconfiguredJobStageParameter(
          mapping: "manifest.spec.template.spec.containers[0].env[1].value",
          defaultValue: overriddenValue,
          name: "envVariable"
        )
      ],
      manifest: new V1Job(spec: [template: [spec: [containers: [new V1Container(env: [new V1EnvVar(name: "foo", value: manifestEnvValue)])]]]])
    )

    def jobService = Mock(JobService) {
      1 * getPreconfiguredStages() >> {
        return [
          property
        ]
      }
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    def ex = thrown(IllegalArgumentException)
    assert ex.getMessage().startsWith("Invalid index 1 for list")
  }

  def "setNestedValue throws an error if an array property name is invalid"() {
    given:
    def manifestEnvValue = "defaultValue"
    def overriddenValue = "newValue"
    def stage = stage {
      type = "test"
      context = [account: "test"]
    }
    def property = new KubernetesPreconfiguredJobProperties(
      enabled: true,
      label: "test",
      type: "test",
      cloudProvider: "kubernetes",
      parameters: [
        new PreconfiguredJobStageParameter(
          mapping: "manifest.spec.template.spec.containers[0].missingProperty[0].value",
          defaultValue: overriddenValue,
          name: "envVariable"
        )
      ],
      manifest: new V1Job(spec: [template: [spec: [containers: [new V1Container(env: [new V1EnvVar(name: "foo", value: manifestEnvValue)])]]]])
    )

    def jobService = Mock(JobService) {
      1 * getPreconfiguredStages() >> {
        return [
          property
        ]
      }
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    def ex = thrown(IllegalArgumentException)
    assert ex.getMessage().startsWith("no property missingProperty on")
  }

  def "setNestedValue throws an error if the root property of a dynamic parameter is invalid"() {
    given:
    def manifestEnvValue = "defaultValue"
    def overriddenValue = "newValue"
    def stage = stage {
      type = "test"
      context = [account: "test", dynamicParameters: ["undefined.parameter": "value"]]
    }
    def property = new KubernetesPreconfiguredJobProperties(
        enabled: true,
        label: "test",
        type: "test",
        cloudProvider: "kubernetes",
        parameters: [
            new PreconfiguredJobStageParameter(
                mapping: "manifest.spec.template.spec.containers[0].env[0].value",
                defaultValue: overriddenValue,
                name: "envVariable"
            )
        ],
        manifest: new V1Job(spec: [template: [spec: [containers: [new V1Container(env: [new V1EnvVar(name: "foo", value: manifestEnvValue)])]]]])
    )

    def jobService = Mock(JobService) {
      1 * getPreconfiguredStages() >> {
        return [
            property
        ]
      }
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    def ex = thrown(IllegalArgumentException)
    assert ex.getMessage().startsWith("no property undefined on")
  }

  def "should serialize kubernetes job manifest correctly"() {
    given:
    def stage = stage {
      type = "test"
      context = [account: "test"]
    }

    def requests = new HashMap<String, Quantity>()
    requests.put("cpu", Quantity.fromString("100m"))

    def limits = new HashMap<String, Quantity>()
    limits.put("cpu", Quantity.fromString("100m"))

    def resourceRequirements = new V1ResourceRequirements(requests: requests, limits: limits)

    def property = new KubernetesPreconfiguredJobProperties(
        enabled: true,
        label: "test",
        type: "test",
        cloudProvider: "kubernetes",
        parameters: [],
        manifest: new V1Job(spec: [template: [spec: [containers: [new V1Container(resources: resourceRequirements)]]]])
    )

    def jobService = Mock(JobService) {
      1 * getPreconfiguredStages() >> {
        return [property]
      }
    }

    when:
    PreconfiguredJobStage preconfiguredJobStage = new PreconfiguredJobStage(Mock(DestroyJobTask), [], Optional.of(jobService))
    preconfiguredJobStage.buildTaskGraph(stage)

    then:
    // verify that stage manifest has the correct requests and limits
    stage.getContext().get("manifest").spec.template.spec.containers[0].resources.requests.cpu == "100m"
    stage.getContext().get("manifest").spec.template.spec.containers[0].resources.limits.cpu == "100m"
  }
}

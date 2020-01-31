/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import retrofit.client.Header
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification

class KubernetesJobRunnerSpec extends Specification {

  def "should return a run job operation if cluster set in context"() {
    given:
    ArtifactUtils artifactUtils = Mock(ArtifactUtils)
    ObjectMapper objectMapper = new ObjectMapper()
    ManifestEvaluator manifestEvaluator = Mock(ManifestEvaluator)
    def stage = new Stage(Execution.newPipeline("test"), "runJob", [
      credentials: "abc", cloudProvider: "kubernetes",
      cluster: [
        foo: "bar"
      ]
    ])
    KubernetesJobRunner kubernetesJobRunner = new KubernetesJobRunner(artifactUtils, objectMapper, manifestEvaluator)

    when:
    def ops = kubernetesJobRunner.getOperations(stage)
    def op = ops.get(0)

    then:
    op.containsKey("runJob") == true
    op.get("runJob").containsKey("foo") == true
    op.get("runJob").get("foo") == "bar"

  }

  def "should return a run job operation with all context"() {
    given:
    ArtifactUtils artifactUtils = Mock(ArtifactUtils)
    ObjectMapper objectMapper = new ObjectMapper()
    ManifestEvaluator manifestEvaluator = Mock(ManifestEvaluator)
    def stage = new Stage(Execution.newPipeline("test"), "runJob", [
      credentials: "abc", cloudProvider: "kubernetes",
      foo: "bar"
    ])
    KubernetesJobRunner kubernetesJobRunner = new KubernetesJobRunner(artifactUtils, objectMapper, manifestEvaluator)

    when:
    def ops = kubernetesJobRunner.getOperations(stage)
    def op = ops.get(0)

    then:
    op.containsKey("runJob") == true
    op.get("runJob").containsKey("foo") == true
    op.get("runJob").get("foo") == "bar"

  }

  def "getAdditionalOutputs should return manifest log template if present"() {
    given:
    ArtifactUtils artifactUtils = Mock(ArtifactUtils)
    ObjectMapper objectMapper = new ObjectMapper()
    ManifestEvaluator manifestEvaluator = Mock(ManifestEvaluator)
    def stage = new Stage(Execution.newPipeline("test"), "runJob", [
      credentials: "abc", cloudProvider: "kubernetes",
      manifest: [
        metadata: [
          annotations:[
            "job.spinnaker.io/logs": "foo"
          ]
        ]
      ]
    ])
    KubernetesJobRunner kubernetesJobRunner = new KubernetesJobRunner(artifactUtils, objectMapper, manifestEvaluator)

    when:
    def ops = kubernetesJobRunner.getOperations(stage)
    def outputs = kubernetesJobRunner.getAdditionalOutputs(stage, ops)

    then:
    outputs.execution == [logs: "foo"]
  }

  def "populates manifest from artifact if artifact source"() {
    given:
    def manifest = [metadata: [name: "manifest"]]
    ArtifactUtils artifactUtils = Mock(ArtifactUtils)
    ObjectMapper objectMapper = new ObjectMapper()
    OortService oortService = Mock(OortService)
    ContextParameterProcessor contextParameterProcessor = Mock(ContextParameterProcessor)
    RetrySupport retrySupport = new RetrySupport()
    ManifestEvaluator manifestEvaluator = new ManifestEvaluator(
      artifactUtils, contextParameterProcessor, oortService, retrySupport
    )
    def stage = new Stage(Execution.newPipeline("test"), "runJob", [
      credentials: "abc", cloudProvider: "kubernetes",
      source: "artifact",
      manifestArtifactId: "foo",
      manifestArtifactAccount: "bar",
    ])
    KubernetesJobRunner kubernetesJobRunner = new KubernetesJobRunner(artifactUtils, objectMapper, manifestEvaluator)

    when:
    def ops = kubernetesJobRunner.getOperations(stage)
    def op = ops.get(0).get("runJob")

    then:
    1 * contextParameterProcessor.process(_, _, _) >> {
      return [manifests: [manifest]]
    }
    1 * oortService.fetchArtifact(_) >> {
      return new Response(
        "http://yoyo",
        200,
        "",
        new ArrayList<Header>(),
        new TypedString('{"metadata": {"name": "manifest"}}')
      )
    }
    1 * artifactUtils.getBoundArtifactForStage(_, _, _) >> {
      return Artifact.builder().build()
    }
    1 * artifactUtils.getArtifacts(_) >> {
      return []
    }
    op.manifest == manifest
    op.requiredArtifacts == []
    op.optionalArtifacts == []
  }
}

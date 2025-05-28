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
import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Specification

class KubernetesJobRunnerSpec extends Specification {
  def "should return a run job operation with all context"() {
    given:
    ArtifactUtils artifactUtils = Mock(ArtifactUtils)
    ObjectMapper objectMapper = new ObjectMapper()
    ManifestEvaluator manifestEvaluator = new ManifestEvaluator(
        Mock(ArtifactUtils) {
          getArtifacts(_ as StageExecution) >> ImmutableList.of()
        },
        Mock(ContextParameterProcessor),
        Mock(OortService),
        new RetrySupport()
    )
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("test"), "runJob", [
      credentials: "abc", cloudProvider: "kubernetes",
      foo: "bar",
      source: "text",
      manifest: [
        metadata: [
          name: "my-job"
        ]
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

  def "getAdditionalOutputs should return manifest log template if present"() {
    given:
    ArtifactUtils artifactUtils = Mock(ArtifactUtils)
    ObjectMapper objectMapper = new ObjectMapper()
    ManifestEvaluator manifestEvaluator = new ManifestEvaluator(
        Mock(ArtifactUtils) {
          getArtifacts(_ as StageExecution) >> ImmutableList.of()
        },
        Mock(ContextParameterProcessor),
        Mock(OortService),
        new RetrySupport()
    )
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("test"), "runJob", [
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
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("test"), "runJob", [
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
      Calls.response(ResponseBody.create(MediaType.parse("application/json"), '{"metadata": {"name": "manifest"}}'))
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

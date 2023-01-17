/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.spinnaker.front50.migrations

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.api.model.Timestamped
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.jackson.mixins.PipelineMixins
import com.netflix.spinnaker.front50.jackson.mixins.TimestampedMixins
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import spock.lang.Specification
import spock.lang.Subject

class RedBlackToBlueGreenK8sPipelinesMigrationSpec extends Specification {


  def pipelineWithHighlanderStrategy = "{\"id\":\"pipeline-1\",\"name\":null,\"application\":\"application1\",\"type\":null,\"schema\":\"1\",\"config\":null,\"triggers\":[],\"index\":null,\"updateTs\":null,\"lastModifiedBy\":null,\"lastModified\":null,\"email\":null,\"disabled\":null,\"template\":null,\"roles\":null,\"serviceAccount\":null,\"executionEngine\":null,\"stageCounter\":null,\"stages\":[{\"cloudProvider\":\"kubernetes\",\"trafficManagement\":{\"options\":{\"strategy\":\"highlander\"},\"enabled\":true},\"type\":\"deployManifest\"}],\"constraints\":null,\"payloadConstraints\":null,\"keepWaitingPipelines\":null,\"limitConcurrent\":null,\"maxConcurrentExecutions\":null,\"parameterConfig\":null,\"spelEvaluator\":null,\"any\":{},\"createdAt\":null}"

  def pipelineDAO = Mock(PipelineDAO)
  def objectMapper = new ObjectMapper().addMixIn(Timestamped.class, TimestampedMixins.class)
    .addMixIn(Pipeline.class, PipelineMixins.class)

  @Subject
  def migration = new RedBlackToBlueGreenK8sPipelinesMigration(pipelineDAO)

  def "should not migrate K8s pipeline that is not using redblack strategy"() {
    given:
    def pipeline = this.objectMapper.readValue(pipelineWithHighlanderStrategy, Pipeline.class)

    when:
    migration.run()

    then:
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update("pipeline-1", _)
    0 * _
  }

  def "should not migrate K8s pipeline when traffic management is not defined"() {
    given:
    def pipelineWithNoTrafficManagement = "{\"id\":\"pipeline-1\",\"name\":null,\"application\":\"application1\",\"type\":null,\"schema\":\"1\",\"config\":null,\"triggers\":[],\"index\":null,\"updateTs\":null,\"lastModifiedBy\":null,\"lastModified\":null,\"email\":null,\"disabled\":null,\"template\":null,\"roles\":null,\"serviceAccount\":null,\"executionEngine\":null,\"stageCounter\":null,\"stages\":[{\"cloudProvider\":\"kubernetes\",\"type\":\"deployManifest\"}],\"constraints\":null,\"payloadConstraints\":null,\"keepWaitingPipelines\":null,\"limitConcurrent\":null,\"maxConcurrentExecutions\":null,\"parameterConfig\":null,\"spelEvaluator\":null,\"any\":{},\"createdAt\":null}"
    def pipeline = this.objectMapper.readValue(pipelineWithNoTrafficManagement, Pipeline.class)

    when:
    migration.run()

    then:
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update("pipeline-1", _)
    0 * _
  }

  def "should not migrate K8s pipeline that doesn't have a deploy manifest stage"() {
    given:
    def pipelineWithBakeManifestStage = "{\"id\":\"pipeline-1\",\"name\":null,\"application\":\"application1\",\"type\":null,\"schema\":\"1\",\"config\":null,\"triggers\":[],\"index\":null,\"updateTs\":null,\"lastModifiedBy\":null,\"lastModified\":null,\"email\":null,\"disabled\":null,\"template\":null,\"roles\":null,\"serviceAccount\":null,\"executionEngine\":null,\"stageCounter\":null,\"stages\":[{\"cloudProvider\":\"kubernetes\",\"type\":\"bakeManifest\"}],\"constraints\":null,\"payloadConstraints\":null,\"keepWaitingPipelines\":null,\"limitConcurrent\":null,\"maxConcurrentExecutions\":null,\"parameterConfig\":null,\"spelEvaluator\":null,\"any\":{},\"createdAt\":null}"
    def pipeline = this.objectMapper.readValue(pipelineWithBakeManifestStage, Pipeline.class)

    when:
    migration.run()

    then:
    1 * pipelineDAO.all() >> { return [pipeline] }
    0 * pipelineDAO.update("pipeline-1", _)
    0 * _
  }

  def "should migrate K8s pipeline that is using redblack strategy"() {
    given:
    def pipelineWithRedBlackStrategy = "{\"id\":\"pipeline-2\",\"name\":null,\"application\":\"application2\",\"type\":null,\"schema\":\"1\",\"config\":null,\"triggers\":[],\"index\":null,\"updateTs\":null,\"lastModifiedBy\":null,\"lastModified\":null,\"email\":null,\"disabled\":null,\"template\":null,\"roles\":null,\"serviceAccount\":null,\"executionEngine\":null,\"stageCounter\":null,\"stages\":[{\"cloudProvider\":\"kubernetes\",\"trafficManagement\":{\"options\":{\"strategy\":\"redblack\"},\"enabled\":true},\"type\":\"deployManifest\"}],\"constraints\":null,\"payloadConstraints\":null,\"keepWaitingPipelines\":null,\"limitConcurrent\":null,\"maxConcurrentExecutions\":null,\"parameterConfig\":null,\"spelEvaluator\":null,\"any\":{},\"createdAt\":null}"
    def expectedPipelineBlueGreenStrategy = "{\"id\":\"pipeline-2\",\"application\":\"application2\",\"schema\":\"1\",\"triggers\":[],\"stages\":[{\"cloudProvider\":\"kubernetes\",\"trafficManagement\":{\"options\":{\"strategy\":\"bluegreen\"},\"enabled\":true},\"type\":\"deployManifest\"}],\"lastModified\":null,\"any\":{}}"
    def pipeline = this.objectMapper.readValue(pipelineWithRedBlackStrategy, Pipeline.class)
    def inputPipeline

    when:
    migration.run()

    then:
    1 * pipelineDAO.all() >> { return [pipeline] }
    1 * pipelineDAO.update("pipeline-2", _) >> { arguments -> inputPipeline = arguments[1] }
    0 * _
    inputPipeline instanceof Pipeline
    this.objectMapper.writeValueAsString(inputPipeline) == expectedPipelineBlueGreenStrategy
  }
}



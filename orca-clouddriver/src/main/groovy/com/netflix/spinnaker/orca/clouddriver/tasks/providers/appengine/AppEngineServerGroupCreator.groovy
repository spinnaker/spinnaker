/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

@Slf4j
@Component
class AppEngineServerGroupCreator implements ServerGroupCreator {
  boolean katoResultExpected = false
  String cloudProvider = 'appengine'

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ArtifactResolver artifactResolver

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    appendArtifactData(stage, operation)
    operation.branch = AppEngineBranchFinder.findInStage(operation, stage) ?: operation.branch

    return [[(OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.empty()
  }

  void appendArtifactData(Stage stage, Map operation) {
    Execution execution = stage.getExecution()
    String expectedId = operation.expectedArtifactId?.trim()
    if (execution.type == PIPELINE && expectedId) {
      Artifact boundArtifact = artifactResolver.getBoundArtifactForId(stage, expectedId)
      if (boundArtifact) {
        operation.artifact = boundArtifact
      } else {
        throw new RuntimeException("Missing bound artifact for ID $expectedId")
      }
    }
  }
}

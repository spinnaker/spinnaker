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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class AppEngineServerGroupCreator implements ServerGroupCreator {
  boolean katoResultExpected = false
  String cloudProvider = 'appengine'

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    resolveArtifacts(stage, operation)
    operation.branch = AppEngineBranchFinder.findInStage(operation, stage) ?: operation.branch

    return [[(OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.empty()
  }

  static private void resolveArtifacts(Stage stage, Map operation) {
    if (operation.repositoryUrl) {
      return
    }

    Map expectedArtifact = operation.expectedArtifact
    if (operation.fromArtifact && expectedArtifact && expectedArtifact.fields) {
      Execution execution = stage.getExecution()
      Map<String, Object> trigger = [:]
      if (execution instanceof Pipeline) {
        trigger = ((Pipeline) execution).getTrigger()
      }

      List<Map> artifacts = (List<Map>) trigger.artifacts
      Artifact artifact = (Artifact) artifacts.find { a -> ((ExpectedArtifact) expectedArtifact).matches((Artifact) a) }
      if (artifact?.reference) {
        String repositoryUrl = ''
        switch (artifact.type) {
          // TODO(jacobkiefer): These object types are pretty fragile, we need to harden this somehow.
          case 'gcs/object':
            if (!artifact.reference.startsWith('gs://')) {
              repositoryUrl = "gs://${artifact.reference}"
            } else {
              repositoryUrl = artifact.reference
            }
            operation.repositoryUrl = repositoryUrl
            break
          default:
            throw new ArtifactResolver.ArtifactResolutionException('Unknown artifact type')
            break
        }
      } else {
        throw new ArtifactResolver.ArtifactResolutionException('Missing artifact reference for artifact: ${artifact}')
      }
    } else {
      throw new ArtifactResolver.ArtifactResolutionException('AppEngine Deploy description missing repositoryUrl but misconfigured for resolving Artifacts')
    }
  }
}

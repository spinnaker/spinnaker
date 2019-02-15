/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class CloudFoundryServerGroupCreator implements ServerGroupCreator {

  boolean katoResultExpected = false
  String cloudProvider = "cloudfoundry"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [
      application: stage.context.application,
      credentials: stage.context.account,
      manifest: stage.context.manifest,
      region: stage.context.region,
      startApplication: stage.context.startApplication,
      artifact: stage.context.artifact
    ]

    stage.context.stack?.with { operation.stack = it }
    stage.context.freeFormDetails?.with { operation.freeFormDetails = it }

    if(stage.execution.trigger instanceof JenkinsTrigger) {
      JenkinsTrigger jenkins = stage.execution.trigger as JenkinsTrigger
      def artifact = stage.context.artifact
      if(artifact.type == 'trigger') {
        operation.artifact = getArtifactFromJenkinsTrigger(jenkins, artifact.account, artifact.pattern)
      }
      def manifest = stage.context.manifest
      if(manifest.type == 'trigger') {
        operation.manifest = getArtifactFromJenkinsTrigger(jenkins, manifest.account, manifest.pattern)
      }
    }

    return [[(OPERATION): operation]]
  }

  private Map getArtifactFromJenkinsTrigger(JenkinsTrigger jenkinsTrigger, String account, String regex) {
    def matchingArtifact = jenkinsTrigger.buildInfo.artifacts.find { it.fileName ==~ regex }
    if(!matchingArtifact) {
      throw new IllegalStateException("No Jenkins artifacts matched the pattern '${regex}'.")
    }
    return [
      type: 'artifact',
      account: account,
      reference: jenkinsTrigger.buildInfo.url + 'artifact/' + matchingArtifact.relativePath
    ]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.empty()
  }
}

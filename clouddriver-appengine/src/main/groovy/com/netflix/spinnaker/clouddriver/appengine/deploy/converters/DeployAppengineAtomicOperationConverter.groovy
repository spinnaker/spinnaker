/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.appengine.AppengineOperation
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppengineDescriptionConversionException
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.DeployAppengineAtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import groovy.util.logging.Slf4j

@AppengineOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
@Slf4j
class DeployAppengineAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Autowired
  ObjectMapper objectMapper

  AtomicOperation convertOperation(Map input) {
    new DeployAppengineAtomicOperation(convertDescription(input))
  }

  DeployAppengineDescription convertDescription(Map input) {
    DeployAppengineDescription description = AppengineAtomicOperationConverterHelper.convertDescription(input, this, DeployAppengineDescription)

    if (input.artifact) {
      description.artifact = objectMapper.convertValue(input.artifact, Artifact)
      switch (description.artifact.type) {
        case 'gcs/object':
          description.repositoryUrl = description.artifact.reference
          if (!description.repositoryUrl.startsWith('gs://')) {
            description.repositoryUrl = "gs://${description.repositoryUrl}"
          }
          break
        case 'docker/image':
          if (description.artifact.reference) {
            description.containerImageUrl = description.artifact.reference
          } else if (description.artifact.name) {
            description.containerImageUrl = description.artifact.name
          }
          break
        default:
          throw new AppengineDescriptionConversionException("Invalid artifact type for Appengine deploy: ${description.artifact.type}")
      }
    }
    if (input.configArtifacts) {
      def configArtifacts = input.configArtifacts
      description.configArtifacts = configArtifacts.collect({ objectMapper.convertValue(it, Artifact) })
    }

    return description
  }
}

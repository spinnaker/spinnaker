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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class GoogleServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  boolean katoResultExpected = false
  String cloudProvider = "gce"

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

    if (operation.account && !operation.credentials) {
      operation.credentials = operation.account
    }

    if (stage.context.imageSource == "artifact") {
      operation.imageSource = "ARTIFACT"
      operation.imageArtifact = getImageArtifact(stage)
    } else {
      operation.imageSource = "STRING"
      operation.image = operation.image ?: getImage(stage)
      if (!operation.image) {
        throw new IllegalStateException("No image could be found in ${stage.context.region}.")
      }
    }

    return [[(ServerGroupCreator.OPERATION): operation]]
  }

  private Artifact getImageArtifact(Stage stage) {
    def stageContext = stage.getContext()

    def artifactId = stageContext.imageArtifactId as String
    if (artifactId == null) {
      throw new IllegalArgumentException("Image source was set to artifact but no artifact was specified.")
    }
    return artifactResolver.getBoundArtifactForId(stage, artifactId)
  }

  private String getImage(Stage stage) {
    String image

    withImageFromPrecedingStage(stage, null, cloudProvider) {
      image = image ?: it.imageId
    }

    withImageFromDeploymentDetails(stage, null, cloudProvider) {
      image = image ?: it.imageId
    }

    return image
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.of("Google")
  }
}

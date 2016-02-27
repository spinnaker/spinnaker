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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.kubernetes

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class KubernetesServerGroupCreator implements ServerGroupCreator {

  boolean katoResultExpected = false
  String cloudProvider = "kubernetes"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    // If this is a stage in a pipeline, look in the context for the baked image.
    def deploymentDetails = (stage.context.deploymentDetails ?: []) as List<Map>

    def containers = (List<Map<String, Object>>) operation.containers

    containers.forEach { container ->
      if (container.imageDescription.fromContext) {
        def image = deploymentDetails.find {
          def cluster = Names.parseName(it.sourceServerGroup).cluster
          cluster == container.imageDescription.cluster && it.imageName ==~ container.imageDescription.pattern
        }
        if (!image) {
          throw new IllegalStateException("No image found in context for pattern $container.imageDescription.pattern.")
        } else {
          container.imageDescription = [registry: image.registry, tag: image.tag, repository: image.repository]
        }
      }
    }

    return [[(ServerGroupCreator.OPERATION): operation]]
  }
}

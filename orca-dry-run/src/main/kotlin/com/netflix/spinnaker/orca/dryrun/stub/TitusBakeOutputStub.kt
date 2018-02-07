/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.dryrun.stub

import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class TitusBakeOutputStub : OutputStub {

  override fun supports(stage: Stage) =
    stage.type == "bake" && stage.context["cloudProviderType"] == "titus"

  override fun outputs(stage: Stage) =
    if (stage.parent?.type == "bake") {
      emptyMap()
    } else {
      val app = stage.execution.application
      mapOf(
        "deploymentDetails" to (stage.regions).map { region ->
          val ami = "$app/basic:master-h${randomNumeric(5)}.${randomHex(7)}"
          mapOf(
            "ami" to ami,
            "imageId" to ami,
            "amiSuffix" to timestamp(),
            "baseOs" to "trusty",
            "storeType" to "docker",
            "region" to region,
            "package" to "ssh://git@my.docker.repo:7999/${app}/docker-build-repo.git?${randomHex(40)}",
            "cloudProviderType" to "titus"
          )
        }
      )
    }
}

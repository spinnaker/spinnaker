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
class BakeOutputStub : OutputStub {

  override fun supports(stageType: String) = stageType == "bake"

  override fun outputs(stage: Stage) =
    if (stage.parent?.type == "bake") {
      emptyMap()
    } else {
      mapOf(
        "deploymentDetails" to (stage.regions).map { region ->
          randomHex(8).let { id ->
            mapOf(
              "ami" to "ami-$id",
              "imageId" to "ami-$id",
              "amiSuffix" to timestamp(),
              "baseLabel" to "release",
              "baseOs" to "trusty",
              "storeType" to "ebs",
              "vmType" to "hvm",
              "region" to region,
              "package" to stage.execution.application,
              "cloudProvider" to "aws"
            )
          }
        }
      )
    }
}

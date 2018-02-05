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
class FindImageFromTagsOutputStub : OutputStub {
  override fun supports(stage: Stage) = stage.type == "findImageFromTags"

  override fun outputs(stage: Stage): Map<String, Any> =
    mapOf(
      "deploymentDetails" to stage.regions.map { region ->
        Pair(randomHex(8), randomNumeric(4)).let { (id, build) ->
          mapOf(
            "imageName" to "${stage.execution.application}-${randomNumeric(1)}.${randomNumeric(4)}.0-h${build}.${randomHex(7)}-x86_64-${timestamp()}-trusty-hvm-sriov-ebs",
            "imageId" to "ami-$id",
            "jenkins" to mapOf(
              "number" to build,
              "host" to "http://jenkins/",
              "name" to "ami-$id"
            ),
            "ami" to "ami-$id",
            "region" to region
          )
        }
      }
    )
}

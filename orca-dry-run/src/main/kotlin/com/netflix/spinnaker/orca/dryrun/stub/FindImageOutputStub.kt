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
class FindImageOutputStub : OutputStub {
  override fun supports(stage: Stage) = stage.type == "findImage"

  override fun outputs(stage: Stage): Map<String, Any> = mapOf(
    "deploymentDetails" to stage.regions.map { region ->
      Pair(randomHex(8), randomNumeric(4)).let { (id, build) ->
        val version = "${randomNumeric(1)}.${randomNumeric(4)}.0"
        val image = "${stage.execution.application}-${version}-h${build}.${randomHex(7)}-x86_64-${timestamp()}-trusty-hvm-sriov-ebs"
        mapOf(
          "ami" to "ami-$id",
          "imageId" to "ami-$id",
          "imageName" to image,
          "cloudProvider" to "aws",
          "sourceServerGroup" to "${stage.context["cluster"]}-v${randomNumeric(3)}",
          "region" to region,
          "virtualizationType" to "hvm",
          "blockDeviceMappings" to emptyList<Map<String, Any>>(),
          "description" to "name=${stage.execution.application}, arch=x86_64, ancestor_name=trustybase-x86_64-${randomNumeric(10)}-ebs, ancestor_id=ami-${randomHex(8)}",
          "enaSupport" to true,
          "ownerId" to randomNumeric(12),
          "creationDate" to "2017-11-29T21:30:42.000Z",
          "imageLocation" to "${randomNumeric(12)}/${image}",
          "rootDeviceType" to "ebs",
          "tags" to emptyList<Map<String, Any>>(),
          "public" to false,
          "sriovNetSupport" to "simple",
          "hypervisor" to "xen",
          "name" to image,
          "rootDeviceName" to "/dev/sda1",
          "productCodes" to emptyList<Any>(),
          "state" to "available",
          "imageType" to "machine",
          "architecture" to "x86_64",
          "package_name" to stage.execution.application,
          "version" to version,
          "commit" to randomHex(7),
          "jenkins" to mapOf(
            "number" to build,
            "host" to "http://jenkins/",
            "name" to "ami-$id"
          )
        )
      }
    }
  )
}

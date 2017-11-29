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

package com.netflix.spinnaker.clouddriver.aws.deploy.preprocessors

import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import org.springframework.stereotype.Component

@Component
class ResizeAsgDescriptionPreProcessor implements AtomicOperationDescriptionPreProcessor {
  @Override
  boolean supports(Class descriptionClass) {
    return descriptionClass == ResizeAsgDescription
  }

  @Override
  Map process(Map description) {
    description.with {
      def c = constraints ? [constraints: constraints] : [:]

      if (!asgs) {
        if (region) {
          asgs = [
            [serverGroupName: serverGroupName ?: asgName, region: region, capacity: capacity] + c
          ]
        } else {
          asgs = regions.collect {
            [serverGroupName: serverGroupName ?: asgName, region: it, capacity: capacity] + c

          }
        }
      }

      // Only `asgs` should be propagated internally now.
      description.remove("asgName")
      description.remove("serverGroupName")
      description.remove("regions")
      description.remove("region")
      description.remove("capacity")
      description.remove("constraints")

      return description
    }
  }
}

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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops.preprocessors

import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.EnableDisableDestroyAzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class RegionsToRegionDescriptionPreProcessor implements AtomicOperationDescriptionPreProcessor {
  @Override
  boolean supports(Class descriptionClass) {
    return descriptionClass == EnableDisableDestroyAzureServerGroupDescription
  }

  @Override
  Map process(Map description) {
    description.with {
      if (!region && regions) {
        region = regions[0]

        if (regions.size() > 1) {
          log.warn("EnableDisableDestroyAzureServerGroupDescription has regions size greater than 1: $regions")
        }
      }

      // Only `region` should be propagated internally now.
      description.remove("regions")

      return description
    }
  }
}

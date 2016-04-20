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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAsgTagsDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DestroyAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResumeAsgProcessesDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.SuspendAsgProcessesDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgTagsDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import org.springframework.stereotype.Component

@Component
class LegacyToAsgsDescriptionPreProcessor implements AtomicOperationDescriptionPreProcessor {
  @Override
  boolean supports(Class descriptionClass) {
    return descriptionClass in [DeleteAsgTagsDescription,
                                DestroyAsgDescription,
                                EnableDisableAsgDescription,
                                ResumeAsgProcessesDescription,
                                SuspendAsgProcessesDescription,
                                UpsertAsgTagsDescription]
  }

  @Override
  Map process(Map description) {
    description.with {
      if (!asgs) {
        if (region) {
          asgs = [[serverGroupName: serverGroupName ?: asgName, region: region]]
        } else {
          asgs = regions.collect {
            [serverGroupName: serverGroupName ?: asgName, region: it]
          }
        }
      }

      // Only `asgs` should be propagated internally now.
      description.remove("asgName")
      description.remove("serverGroupName")
      description.remove("regions")
      description.remove("region")

      return description
    }
  }
}

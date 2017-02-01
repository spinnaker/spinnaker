/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mahe.cleanup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.PropertyAction
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class FastPropertyCleanupListener implements ExecutionListener {

  private final MaheService mahe

  @Autowired
  FastPropertyCleanupListener(MaheService mahe) {
    this.mahe = mahe
  }

  @Override
  void afterExecution(Persister persister,
                      Execution execution,
                      ExecutionStatus executionStatus,
                      boolean wasSuccessful) {


    if (executionStatus in [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED] || execution.context.rollbackProperties) {
      execution.with {
        context.propertyIdList.each { id ->
          def originalProperty = context.originalProperties.find {prop -> prop.propertyId == id}
          switch (execution.context.propertyAction) {
            case PropertyAction.CREATE:
              mahe.deleteProperty(id, "spinnaker rollback", extractEnvironment(id))
              break;
            case [PropertyAction.UPDATE, PropertyAction.DELETE]:
              if(originalProperty) {
                mahe.upsertProperty([property: originalProperty])
              }
              break
          }
        }
      }
    }
  }

  private String extractEnvironment(propertyId) {
    propertyId.find(~/\w+\|\w+\|(\w+)\|.*?/) { match, env ->
      env
    }
  }
}

/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.keel.veto

import com.netflix.spinnaker.keel.api.Resource
import org.springframework.stereotype.Component

/**
 * The veto enforcer picks up all vetos, and given a resource name
 * it looks at each veto to decide if we can check that resource.
 *
 * One "false" overrides all "true"s
 */
@Component
class VetoEnforcer(
  val vetos: List<Veto>
) {

  fun canCheck(resource: Resource<*>): VetoResponse {
    vetos.forEach { veto ->
      val response = veto.check(resource)
      if (!response.allowed) {
        return response
      }
    }
    return VetoResponse(allowed = true, vetoName = "all")
  }
}

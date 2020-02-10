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
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id

class DummyVeto(
  private val allowAll: Boolean
) : Veto {
  override fun check(resource: Resource<*>): VetoResponse =
    check(resource.id, resource.application)

  override fun check(resourceId: String, application: String): VetoResponse =
    if (allowAll) {
      allowedResponse()
    } else {
      deniedResponse("None shall pass")
    }

  override fun messageFormat(): Map<String, Any> {
    TODO("not implemented")
  }

  override fun passMessage(message: Map<String, Any>) {
    TODO("not implemented")
  }

  override fun currentRejections(): List<String> {
    TODO("not implemented")
  }

  override fun currentRejectionsByApp(application: String): List<String> {
    TODO("not implemented")
  }
}

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
package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ApplicationVetoRepository
import org.springframework.stereotype.Component

@Component
class InMemoryApplicationVetoRepository : ApplicationVetoRepository {

  private val optedOut: MutableSet<String> = mutableSetOf()

  override fun appVetoed(application: String): Boolean =
    optedOut.contains(application)

  override fun optOut(application: String) {
    optedOut.add(application)
  }

  override fun optIn(application: String) {
    optedOut.remove(application)
  }

  override fun getAll(): Set<String> {
    return optedOut
  }

  fun flush() = optedOut.clear()
}

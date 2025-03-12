/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.gate.plugins.deck

import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * Thrown when the local cache has not been populated yet, but an external request has been made to get a manifest
 * or asset. Clients should retry the request.
 */
class CacheNotReadyException : SystemException("Deck plugin cache has not been populated yet") {
  override fun getRetryable(): Boolean? = true
}

/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.bakery.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * The state of a bake as returned by the Bakery API when a bake is created. Once complete it provides a link to the
 * details of the actual bake via {@link BakeStatus#resourceId}.
 */
@CompileStatic
@EqualsAndHashCode(includes = "id")
@ToString(includeNames = true)
class BakeStatus implements Serializable {

  /**
   * The bake status id.
   */
  String id

  State state

  Result result

  /**
   * The bake id that can be used to find the details of the bake.
   *
   * @see BakeryService#lookupBake
   */
  String resourceId

  static enum State {
    PENDING, RUNNING, COMPLETED, SUSPENDED, CANCELLED
  }

  static enum Result {
    SUCCESS, FAILURE
  }
}

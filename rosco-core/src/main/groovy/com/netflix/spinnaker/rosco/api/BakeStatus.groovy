/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.rosco.api

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.swagger.annotations.ApiModelProperty

/**
 * The state of a bake as returned by the Bakery API when a bake is created. Once complete it provides a link to the
 * details of the actual bake via {@link BakeStatus#resource_id}.
 */
@CompileStatic
@EqualsAndHashCode(includes = "id")
@ToString(includeNames = true)
class BakeStatus implements Serializable {

  /**
   * The bake status id.
   */
  @ApiModelProperty(value="The id of the bake request.")
  String id

  State state

  Result result

  /**
   * The bake id that can be used to find the details of the bake.
   *
   * @see BakeryController#lookupBake
   */
  @ApiModelProperty(value="The id of the bake job. Can be passed to lookupBake() to retrieve the details of the newly-baked image.")
  String resource_id

  @JsonIgnore
  String outputContent

  @JsonIgnore
  String logsContent

  @JsonIgnore
  long createdTimestamp

  @JsonIgnore
  long updatedTimestamp

  static enum State {
    RUNNING, COMPLETED, CANCELED
  }

  static enum Result {
    SUCCESS, FAILURE
  }
}

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

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.swagger.annotations.ApiModelProperty
import com.netflix.spinnaker.kork.artifacts.model.Artifact

/**
 * The details of a completed bake.
 *
 * @see BakeryController#lookupBake
 */
@CompileStatic
@EqualsAndHashCode(includes = "id")
@ToString(includeNames = true)
class Bake {
  @ApiModelProperty(value="The id of the bake job.")
  String id
  String ami
  String image_name
  List <String> regions
  Artifact artifact

  @JsonInclude(JsonInclude.Include.NON_NULL)
  List<Artifact> artifacts

  @JsonInclude(JsonInclude.Include.NON_NULL)
  String base_ami
}

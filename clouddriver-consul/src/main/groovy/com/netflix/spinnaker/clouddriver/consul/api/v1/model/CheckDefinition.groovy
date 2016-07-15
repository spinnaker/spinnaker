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

package com.netflix.spinnaker.clouddriver.consul.api.v1.model

import com.fasterxml.jackson.annotation.JsonProperty

class CheckDefinition {
  @JsonProperty("ID")
  String iD

  @JsonProperty("Name")
  String name

  @JsonProperty("Notes")
  String notes

  @JsonProperty("Script")
  String script

  @JsonProperty("DockerContainerID")
  String dockerContainerID

  @JsonProperty("Shell")
  String shell

  @JsonProperty("HTTP")
  String http

  @JsonProperty("TCP")
  String tcp

  @JsonProperty("Interval")
  String interval

  @JsonProperty("TTL")
  String ttl
}

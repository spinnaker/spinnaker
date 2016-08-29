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

// JsonProperty seems to misbehave here. The field names are capitalized, but their json counterparts are lower-case
// because Consul returns field names with Go-naming conventions.
class CheckResult {
  @JsonProperty("node")
  String Node

  @JsonProperty("checkID")
  String CheckID

  @JsonProperty("name")
  String Name

  @JsonProperty("status")
  Status Status

  @JsonProperty("notes")
  String Notes

  @JsonProperty("output")
  String Output

  @JsonProperty("serviceID")
  String ServiceID

  @JsonProperty("serviceName")
  String ServiceName

  enum Status {
    passing,
    critical,
    warning,
    unknown,
  }
}

/*
 * Copyright 2019 Andres Castano
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

package com.netflix.spinnaker.gate.services.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GoogleCloudBuildTrigger {
  GoogleCloudBuildTrigger() {}

  public GoogleCloudBuildTrigger(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
  }

  @JsonProperty private String id;

  @JsonProperty private String name;

  @JsonProperty private String description;
}

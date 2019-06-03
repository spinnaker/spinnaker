/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum DeployOption {
  OMIT_CONFIG("OMIT_CONFIG"),
  FLUSH_INFRASTRUCTURE_CACHES("FLUSH_INFRASTRUCTURE_CACHES"),
  DELETE_ORPHANED_SERVICES("DELETE_ORPHANED_SERVICES"),
  WAIT_FOR_COMPLETION("WAIT_FOR_COMPLETION");

  final String name;

  DeployOption(String name) {
    this.name = name;
  }

  @JsonValue
  public String toString() {
    return name;
  }

  public static DeployOption fromString(String name) {
    return Arrays.stream(values())
        .filter(o -> o.toString().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("There is no DeployType with name " + name));
  }
}

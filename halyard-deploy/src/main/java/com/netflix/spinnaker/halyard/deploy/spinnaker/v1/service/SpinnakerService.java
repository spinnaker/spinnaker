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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
abstract public class SpinnakerService<T> {
  String protocol = "http";
  abstract public int getPort();
  abstract public String getAddress();
  abstract public String getHost();
  abstract public String getHttpHealth();
  List<String> profiles = new ArrayList<>();
  Map<String, String> env = new HashMap<>();
  boolean monitoringEnabled = true;

  @JsonIgnore
  public abstract SpinnakerArtifact getArtifact();

  @JsonIgnore
  public abstract String getName();

  public String getBaseUrl() {
    return getProtocol() + "://" + getAddress() + ":" + getPort();
  }

  abstract public Class<T> getEndpointClass();
}

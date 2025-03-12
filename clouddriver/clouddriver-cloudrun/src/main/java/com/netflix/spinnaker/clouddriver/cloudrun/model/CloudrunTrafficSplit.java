/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CloudrunTrafficSplit implements Cloneable {

  private List<TrafficTarget> trafficTargets = new ArrayList<>();

  @Override
  public CloudrunTrafficSplit clone() {
    try {
      CloudrunTrafficSplit clone = (CloudrunTrafficSplit) super.clone();
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TrafficTarget {
    private String revisionName;
    private Integer percent;
    private Boolean latestRevision;
  }
}

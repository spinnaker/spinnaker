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

package com.netflix.spinnaker.orca.api.preconfigured.jobs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class TitusPreconfiguredJobProperties extends PreconfiguredJobStageProperties {

  private Cluster cluster = new Cluster();

  public TitusPreconfiguredJobProperties(String label, String type) {
    super(label, type, "titus");
  }

  @Override
  public List<String> getOverridableFields() {
    List<String> overrideableFields = new ArrayList<>(Arrays.asList("cluster"));
    overrideableFields.addAll(super.getOverridableFields());
    return overrideableFields;
  }

  @Override
  public boolean isValid() {
    return super.isValid() && this.cluster.isValid();
  }

  @Data
  @NoArgsConstructor
  public class Cluster {

    String imageId;

    String application;

    String stack;

    String region;

    Integer runtimeLimitSecs = 3600;

    Integer retries = 2;

    Map<String, String> env;

    private Resources resources = new Resources();

    private Capacity capacity = new Capacity();

    boolean isValid() {
      return imageId != null
          && !imageId.isEmpty()
          && application != null
          && !application.isEmpty()
          && region != null
          && !region.isEmpty();
    }

    @Data
    @NoArgsConstructor
    public class Resources {

      private Integer cpu = 1;

      private Integer disk = 10000;

      Integer gpu = 0;

      Integer memory = 512;

      Integer networkMbps = 128;
    }

    @NoArgsConstructor
    @Data
    public class Capacity {
      private Integer desired = 1;

      private Integer max = 1;

      private Integer min = 1;
    }
  }
}

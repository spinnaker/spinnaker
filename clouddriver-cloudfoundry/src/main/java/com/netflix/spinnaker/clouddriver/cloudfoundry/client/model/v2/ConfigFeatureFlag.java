/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import static java.util.Arrays.stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class ConfigFeatureFlag {
  ConfigFlag name;
  boolean enabled;

  public enum ConfigFlag {
    SERVICE_INSTANCE_SHARING("service_instance_sharing");

    private final String type;

    ConfigFlag(String type) {
      this.type = type;
    }

    @Nullable
    @JsonCreator
    public static ConfigFlag fromType(String type) {
      return stream(ConfigFlag.values())
          .filter(st -> st.type.equalsIgnoreCase(type))
          .findFirst()
          .orElse(null);
    }
  }
}

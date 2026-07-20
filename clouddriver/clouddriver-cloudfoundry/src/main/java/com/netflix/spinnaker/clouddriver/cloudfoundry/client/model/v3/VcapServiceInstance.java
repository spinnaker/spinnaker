/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import static java.util.Arrays.stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * Represents a bound service instance entry as it appears inside the {@code VCAP_SERVICES}
 * environment variable payload returned by {@code GET /v3/apps/{guid}/env}. This shape is distinct
 * from the top-level v3 {@link ServiceInstance} resource.
 */
@Data
public class VcapServiceInstance {
  private String name;

  @Nullable private Set<String> tags;

  private String plan;
  private String servicePlanGuid;
  private LastOperation lastOperation;
  private Type type;

  public enum Type {
    MANAGED_SERVICE_INSTANCE("managed_service_instance"),
    USER_PROVIDED_SERVICE_INSTANCE("user_provided_service_instance");

    private final String type;

    Type(String type) {
      this.type = type;
    }

    @Nullable
    @JsonCreator
    public static Type fromType(String type) {
      return stream(Type.values())
          .filter(st -> st.type.equalsIgnoreCase(type))
          .findFirst()
          .orElse(null);
    }
  }
}

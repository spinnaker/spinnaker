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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * This is a union type of the code/description mechanisms
 * for Cloud Foundry API v2 and v3.
 */
@Setter
public class ErrorDescription {
  /**
   * Cloud Foundry API v2.
   */
  @Nullable
  private String description;

  /**
   * Cloud Foundry API v2.
   */
  @Nullable
  private Code code;

  /**
   * UAA API
   */
  @Nullable
  private String errorDescription;

  /**
   * Cloud Foundry API v3.
   */
  @Nullable
  private List<Detail> errors;

  /**
   * Cloud Foundry API v3.
   */
  @Nullable
  private Code errorCode;

  @Setter
  public static class Detail {
    private String detail;
  }

  @Nullable
  public Code getCode() {
    return code == null ? errorCode : code;
  }

  public List<String> getErrors() {
    if (description == null) {
      if (errors == null) {
        return errorDescription == null ? emptyList() : singletonList(errorDescription);
      }
      return errors.stream().map(detail -> detail.detail).collect(Collectors.toList());
    }
    return singletonList(description);
  }

  public enum Code {
    ROUTE_HOST_TAKEN("CF-RouteHostTaken"),
    ROUTE_PATH_TAKEN("CF-RoutePathTaken"),
    ROUTE_PORT_TAKEN("CF-RoutePortTaken"),
    RESOURCE_NOT_FOUND("CF-ResourceNotFound");

    private final String code;

    Code(String code) {
      this.code = code;
    }

    @Nullable
    @JsonCreator
    public static Code fromCode(String code) {
      return stream(Code.values()).filter(st -> st.code.equals(code)).findFirst().orElse(null);
    }
  }
}

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

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * This is a union type of the code/description mechanisms for Cloud Foundry API v2 and v3. V3 {
 * "errors": [ { "code": 10008, "title": "CF-UnprocessableEntity", "detail": "something went wrong"
 * } ] }
 *
 * <p>V2 { "description": "The route is invalid: host is required for shared-domains", "error_code":
 * "CF-RouteInvalid", "code": 210001 }
 *
 * <p>UAA { "error_description":"Password must contain at least 1 special characters.",
 * "error":"invalid_password", <- no logic for this right now -- not needed "message":"Password must
 * contain at least 1 special characters." }
 */
@Setter
public class ErrorDescription {
  /** Cloud Foundry API v2. */
  @Nullable private String description;

  /** Cloud Foundry API v2. */
  @JsonProperty("error_code")
  @Nullable
  private Code errorCode;

  /** Cloud Foundry API v2 & v3. */
  @Nullable private int code;

  /** UAA API */
  @JsonProperty("error_description")
  @Nullable
  private String errorDescription;

  /** Cloud Foundry API v3. */
  @Nullable private List<ErrorDescription> errors;

  /** Cloud Foundry API v3. */
  @Getter @Nullable private Code title;

  /** Cloud Foundry API v3. */
  @Getter @Nullable private String detail;

  @Nullable
  public Code getCode() {
    return errors != null && !errors.isEmpty() ? errors.get(0).getTitle() : errorCode;
  }

  public List<String> getErrors() {
    // v2 error
    if (description != null) {
      return singletonList(description);
    }

    // v3 error
    if (errors != null && !errors.isEmpty()) {
      return errors.stream().map(e -> e.getDetail()).collect(Collectors.toList());
    }

    // UAA error
    if (errorDescription != null) {
      return singletonList(errorDescription);
    }

    return emptyList();
  }

  public enum Code {
    ROUTE_HOST_TAKEN("CF-RouteHostTaken"),
    ROUTE_PATH_TAKEN("CF-RoutePathTaken"),
    ROUTE_PORT_TAKEN("CF-RoutePortTaken"),
    RESOURCE_NOT_FOUND("CF-ResourceNotFound"),
    SERVICE_ALREADY_EXISTS("60002"),
    SERVICE_INSTANCE_ALREADY_BOUND("CF-ServiceBindingAppServiceTaken");

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

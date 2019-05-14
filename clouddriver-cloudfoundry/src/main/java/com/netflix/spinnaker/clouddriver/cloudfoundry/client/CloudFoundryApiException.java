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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static java.util.Arrays.stream;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public class CloudFoundryApiException extends RuntimeException {
  private static final String UNKNOWN_ERROR = "Unknown Error";

  @Nullable private ErrorDescription.Code errorCode;

  public CloudFoundryApiException(ErrorDescription errorCause) {
    super(
        Optional.ofNullable(errorCause)
            .map(e -> getMessage(e.getErrors().toArray(new String[0])))
            .orElse(UNKNOWN_ERROR));
    if (errorCause != null) {
      this.errorCode = errorCause.getCode();
    }
  }

  public CloudFoundryApiException(Throwable t, String... errors) {
    super(getMessage(t, errors), t);
  }

  public CloudFoundryApiException(String... errors) {
    super(getMessage(errors));
  }

  private static String getMessage(String... errors) {
    return "Cloud Foundry API returned with error(s): "
        + stream(errors).filter(Objects::nonNull).collect(Collectors.joining(" and "));
  }

  private static String getMessage(Throwable t, String... errors) {
    String[] allErrors = Arrays.copyOf(errors, errors.length + 1);
    allErrors[errors.length] = t.getMessage();
    return getMessage(allErrors);
  }
}

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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

@Getter
public class CloudFoundryApiException extends RuntimeException {
  @Nullable
  private ErrorDescription.Code errorCode;

  public CloudFoundryApiException(ErrorDescription errorCause) {
    super(getMessage(errorCause.getErrors().toArray(new String[0])));
    this.errorCode = errorCause.getCode();
  }

  public CloudFoundryApiException(Throwable t, String... errors) {
    super(getMessage(errors), t);
  }

  public CloudFoundryApiException(String... errors) {
    super(getMessage(errors));
  }

  private static String getMessage(String... errors) {
    return "Cloud Foundry API returned with error(s): \n " +
      stream(errors).filter(Objects::nonNull).map(err -> "* " + err).collect(Collectors.joining("\n "));
  }
}

/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.googlecommon.deploy;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Value;

@NonnullByDefault
public class GoogleApiException extends IOException {
  GoogleApiException(String message) {
    super(message);
  }

  static GoogleApiException fromGoogleJsonException(GoogleJsonResponseException e) {
    ErrorDetails errorDetails = ErrorDetails.fromGoogleJsonException(e);
    if (errorDetails.getStatusCode() == 404) {
      return new NotFoundException(errorDetails.toString());
    }
    if (errorDetails.getReason().equals("resourceInUseByAnotherResource")) {
      return new ResourceInUseException(errorDetails.toString());
    }
    return new GoogleApiException(errorDetails.toString());
  }

  @Value
  private static class ErrorDetails {
    private final int statusCode;
    private final String message;
    private final String reason;

    @ParametersAreNullableByDefault
    private ErrorDetails(int statusCode, String message, String reason) {
      this.statusCode = statusCode;
      this.message = Strings.nullToEmpty(message);
      this.reason = Strings.nullToEmpty(reason);
    }

    static ErrorDetails fromGoogleJsonException(GoogleJsonResponseException e) {
      Optional<ErrorInfo> optionalErrorInfo =
          Optional.ofNullable(e.getDetails()).map(GoogleJsonError::getErrors)
              .orElse(ImmutableList.of()).stream()
              .findFirst();

      if (optionalErrorInfo.isPresent()) {
        ErrorInfo errorInfo = optionalErrorInfo.get();
        return new ErrorDetails(e.getStatusCode(), errorInfo.getMessage(), errorInfo.getReason());
      } else {
        return new ErrorDetails(e.getStatusCode(), e.getMessage(), "");
      }
    }

    @Override
    public String toString() {
      String base =
          String.format(
              "Operation failed. Last attempt returned status code %s with error message %s",
              statusCode, message);
      if (Strings.isNullOrEmpty(reason)) {
        return String.format("%s.", base);
      } else {
        return String.format("%s and reason %s.", base, reason);
      }
    }
  }

  public static final class ResourceInUseException extends GoogleApiException {
    ResourceInUseException(String message) {
      super(message);
    }
  }

  public static final class NotFoundException extends GoogleApiException {
    NotFoundException(String message) {
      super(message);
    }
  }
}

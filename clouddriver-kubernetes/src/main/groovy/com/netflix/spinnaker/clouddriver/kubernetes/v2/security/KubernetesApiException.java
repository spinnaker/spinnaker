/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Status;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesApiException extends RuntimeException {
  private static final ObjectMapper mapper = new ObjectMapper();

  public KubernetesApiException(String operation, Throwable e) {
    super(String.format("%s failed: %s", operation, e.getMessage()), e);
  }

  public KubernetesApiException(String operation, ApiException e) {
    super(
        String.format("%s failed (%d %s): %s", operation, e.getCode(), e.getMessage(), message(e)),
        e);
  }

  private static String message(ApiException e) {
    String responseBody = e.getResponseBody();
    try {
      V1Status status = mapper.readValue(responseBody, V1Status.class);
      return status.getMessage();
    } catch (IOException ioe) {
      log.warn("ApiException encountered that can't be parsed into a V1Status", e);
      return responseBody;
    }
  }
}

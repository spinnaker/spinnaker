/*
 * Copyright 2023 Salesforce, Inc.
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
 */

package com.netflix.spinnaker.orca.retrofit.exceptions

import com.netflix.spinnaker.orca.exceptions.ExceptionHandler
import static java.net.HttpURLConnection.*

abstract class BaseRetrofitExceptionHandler implements ExceptionHandler {
  boolean shouldRetry(Exception e, String kind, Integer responseCode) {
    return shouldRetry(e, kind, null, responseCode)
  }

  boolean shouldRetry(Exception e, String kind, String httpMethod, Integer responseCode) {
    if (isMalformedRequest(kind, e.getMessage())) {
      return false
    }

    // retry on 503 even for non-idempotent requests
    if ("HTTP".equals(kind) && responseCode == HTTP_UNAVAILABLE) {
      return true
    }

    return isIdempotentRequest(httpMethod) && (isNetworkError(kind) || isGatewayErrorCode(kind, responseCode) || isThrottle(kind, responseCode))
  }

  private boolean isGatewayErrorCode(String kind, Integer responseCode) {
    "HTTP".equals(kind) && responseCode in [HTTP_BAD_GATEWAY, HTTP_UNAVAILABLE, HTTP_GATEWAY_TIMEOUT]
  }

  private static final int HTTP_TOO_MANY_REQUESTS = 429

  boolean isThrottle(String kind, Integer responseCode) {
    "HTTP".equals(kind) && responseCode == HTTP_TOO_MANY_REQUESTS
  }

  private boolean isNetworkError(String kind) {
    "NETWORK".equals(kind)
  }

  private boolean isMalformedRequest(String kind, String exceptionMessage) {
    // We never want to retry errors like "Path parameter "blah" value must not be null.
    return "UNEXPECTED".equals(kind) && exceptionMessage?.contains("Path parameter")
  }

  private static boolean isIdempotentRequest(String httpMethod) {
    httpMethod in ["GET", "HEAD", "DELETE", "PUT"]
  }
}

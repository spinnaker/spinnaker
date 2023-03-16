/*
 * Copyright 2023 Armory, Inc
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

package com.netflix.spinnaker.fiat.shared;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

class HeadersRedactor {

  private static final String WHITELIST_HEADER_PREFIX = "X-";
  private static final String SECRET_DATA_VALUE = "**REDACTED**";

  public Map<String, String> getRedactedHeaders(HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();

    if (request.getHeaderNames() != null) {
      for (Enumeration<String> h = request.getHeaderNames(); h.hasMoreElements(); ) {
        String headerName = h.nextElement();
        String headerValue = getRedactedHeaderValue(headerName, request.getHeader(headerName));
        headers.put(headerName, headerValue);
      }
    }
    return headers;
  }

  private String getRedactedHeaderValue(String headerName, String headerValue) {
    if (!headerName.startsWith(WHITELIST_HEADER_PREFIX)) {
      return SECRET_DATA_VALUE;
    } else {
      return headerValue;
    }
  }
}

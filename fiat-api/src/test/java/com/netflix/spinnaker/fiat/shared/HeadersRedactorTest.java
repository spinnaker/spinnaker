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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class HeadersRedactorTest {

  MockHttpServletRequest request = new MockHttpServletRequest();
  HeadersRedactor unit = new HeadersRedactor();

  @Test
  public void verifyNoSecretDataIsShownTest() {
    request.addHeader("Content-Type", "text/html");
    request.addHeader("Authorization", "bearer token");
    request.addHeader("Proxy-Authorization", "bearer token");
    request.addHeader("X-Frame-Options", "DENY");

    Map<String, String> result = unit.getRedactedHeaders(request);

    assertEquals("**REDACTED**", result.get("Content-Type"));
    assertEquals("**REDACTED**", result.get("Authorization"));
    assertEquals("**REDACTED**", result.get("Proxy-Authorization"));
    assertEquals("DENY", result.get("X-Frame-Options"));
  }
}

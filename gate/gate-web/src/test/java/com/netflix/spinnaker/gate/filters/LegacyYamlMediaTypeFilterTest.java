/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.filters;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * TODO: remove this test together with {@link LegacyYamlMediaTypeFilter} after the 2027.0.0
 * release.
 */
class LegacyYamlMediaTypeFilterTest {

  private HttpServletRequest run(MockHttpServletRequest request) throws Exception {
    var seen = new AtomicReference<HttpServletRequest>();
    new LegacyYamlMediaTypeFilter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            new MockFilterChain() {
              @Override
              public void doFilter(
                  jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set((HttpServletRequest) req);
              }
            });
    return seen.get();
  }

  @Test
  void rewritesLegacyContentTypeAndAccept() throws Exception {
    var request = new MockHttpServletRequest("POST", "/managed/resources/diff");
    request.setContentType("application/x-yaml;charset=UTF-8");
    request.addHeader("Content-Type", "application/x-yaml;charset=UTF-8");
    request.addHeader("Accept", "application/x-yaml, application/json");

    var seen = run(request);

    assertThat(seen.getContentType()).isEqualTo("application/yaml;charset=UTF-8");
    assertThat(seen.getHeader("Content-Type")).isEqualTo("application/yaml;charset=UTF-8");
    assertThat(Collections.list(seen.getHeaders("Accept")))
        .containsExactly("application/yaml, application/json");
  }

  @Test
  void leavesOtherRequestsUntouched() throws Exception {
    var request = new MockHttpServletRequest("POST", "/x");
    request.addHeader("Content-Type", "application/json");

    assertThat(run(request)).isSameAs(request);
  }
}

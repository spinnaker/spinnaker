/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class HeaderServiceCallerResolverTest {

  private final HeaderServiceCallerResolver resolver =
      new HeaderServiceCallerResolver(
          "X-Forwarded-Client-Cert", new SpinnakerServiceMapper("spin-"));

  @Test
  void parsesSpiffeIdFromXfccUriField() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        "X-Forwarded-Client-Cert",
        "By=spiffe://td/ns/spinnaker/sa/front50;Hash=abc123;"
            + "URI=spiffe://td/ns/spinnaker/sa/orca");

    Optional<ServiceCaller> caller = resolver.resolve(request);

    assertThat(caller).isPresent();
    assertThat(caller.get().service()).isEqualTo(SpinnakerService.ORCA);
    assertThat(caller.get().source()).isEqualTo("header");
  }

  @Test
  void treatsPlainHeaderValueAsServiceName() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-Client-Cert", "echo");

    Optional<ServiceCaller> caller = resolver.resolve(request);

    assertThat(caller).isPresent();
    assertThat(caller.get().service()).isEqualTo(SpinnakerService.ECHO);
  }

  @Test
  void unrecognizedServiceResolvesToUnknown() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-Client-Cert", "URI=spiffe://td/ns/spinnaker/sa/mallory");

    Optional<ServiceCaller> caller = resolver.resolve(request);

    assertThat(caller).isPresent();
    assertThat(caller.get().service()).isEqualTo(SpinnakerService.UNKNOWN);
  }

  @Test
  void emptyWhenHeaderMissing() {
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }
}

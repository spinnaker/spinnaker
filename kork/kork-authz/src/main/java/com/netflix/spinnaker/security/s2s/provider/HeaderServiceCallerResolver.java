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

import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.ServiceCallerResolver;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Resolves the service caller from a header injected by a service mesh or trusted reverse proxy
 * (Istio/Linkerd/Envoy), which terminated mTLS and vouches for the peer.
 *
 * <p>Understands Envoy's {@code X-Forwarded-Client-Cert} (XFCC) format, extracting the {@code URI=}
 * field (a SPIFFE ID such as {@code spiffe://td/ns/spinnaker/sa/orca}) and using its final path
 * segment as the service name. If the header is not in XFCC form, its whole value is treated as the
 * service name.
 *
 * <p>This provider trusts the header, so it must only be enabled where the header is guaranteed to
 * be set by the mesh/proxy and stripped from any externally originating request.
 */
public class HeaderServiceCallerResolver implements ServiceCallerResolver {

  static final String SOURCE = "header";

  private final String headerName;
  private final SpinnakerServiceMapper mapper;

  public HeaderServiceCallerResolver(String headerName, SpinnakerServiceMapper mapper) {
    this.headerName = headerName;
    this.mapper = mapper;
  }

  @Override
  public Optional<ServiceCaller> resolve(HttpServletRequest request) {
    String value = request.getHeader(headerName);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String identity = extractIdentity(value.trim());
    if (identity.isEmpty()) {
      return Optional.empty();
    }
    SpinnakerService service = mapper.map(serviceNameOf(identity));
    return Optional.of(new ServiceCaller(service, identity, SOURCE));
  }

  /** Pulls the {@code URI=} field out of an XFCC header, else returns the raw value. */
  private static String extractIdentity(String headerValue) {
    for (String element : headerValue.split(";")) {
      String trimmed = element.trim();
      if (trimmed.regionMatches(true, 0, "URI=", 0, 4)) {
        return unquote(trimmed.substring(4).trim());
      }
    }
    return unquote(headerValue);
  }

  /** For a SPIFFE-style URI, the service name is the final path segment; else the whole value. */
  private static String serviceNameOf(String identity) {
    int slash = identity.lastIndexOf('/');
    return slash >= 0 && slash < identity.length() - 1 ? identity.substring(slash + 1) : identity;
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}

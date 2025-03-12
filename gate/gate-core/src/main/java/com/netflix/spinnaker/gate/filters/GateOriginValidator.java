/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.filters;

import java.net.URI;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.springframework.util.StringUtils;

public class GateOriginValidator implements OriginValidator {
  private final URI deckUri;
  private final Pattern redirectHosts;
  private final Pattern allowedOrigins;
  private final boolean expectLocalhost;

  public GateOriginValidator(
      @Nullable String deckUri,
      @Nullable String redirectHostsPattern,
      @Nullable String allowedOriginsPattern,
      boolean expectLocalhost) {
    this.deckUri = deckUri != null ? URI.create(deckUri) : null;
    this.redirectHosts =
        redirectHostsPattern != null ? Pattern.compile(redirectHostsPattern) : null;
    this.allowedOrigins =
        allowedOriginsPattern != null ? Pattern.compile(allowedOriginsPattern) : null;
    this.expectLocalhost = expectLocalhost;
  }

  public boolean isExpectedOrigin(String origin) {
    if (!StringUtils.hasLength(origin)) {
      return false;
    }

    if (deckUri == null) {
      return false;
    }

    try {
      URI uri = URI.create(origin);
      if (!StringUtils.hasLength(uri.getScheme()) || !StringUtils.hasLength(uri.getHost())) {
        return false;
      }

      if (expectLocalhost && uri.getHost().equalsIgnoreCase("localhost")) {
        return true;
      }

      return deckUri.getScheme().equals(uri.getScheme())
          && deckUri.getHost().equals(uri.getHost())
          && deckUri.getPort() == uri.getPort();
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  @Override
  public boolean isValidOrigin(String origin) {
    if (!StringUtils.hasLength(origin)) {
      return false;
    }

    try {
      URI uri = URI.create(origin);
      if (!StringUtils.hasLength(uri.getScheme()) || !StringUtils.hasLength(uri.getHost())) {
        return false;
      }

      if (allowedOrigins != null) {
        return allowedOrigins.matcher(origin).matches();
      }

      if (redirectHosts != null) {
        return redirectHosts.matcher(uri.getHost()).matches();
      }

      if (deckUri == null) {
        return false;
      }

      return deckUri.getScheme().equals(uri.getScheme())
          && deckUri.getHost().equals(uri.getHost())
          && deckUri.getPort() == uri.getPort();
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }
}

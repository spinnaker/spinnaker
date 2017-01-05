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

package com.netflix.spinnaker.gate.filters

import java.util.regex.Pattern

class GateOriginValidator implements OriginValidator {
  private final URI deckUri
  private final Pattern redirectHosts
  private final Pattern allowedOrigins

  GateOriginValidator(String deckUri, String redirectHostsPattern, String allowedOriginsPattern) {
    this.deckUri = deckUri ? deckUri.toURI() : null
    this.redirectHosts = redirectHostsPattern ? Pattern.compile(redirectHostsPattern) : null
    this.allowedOrigins = allowedOriginsPattern ? Pattern.compile(allowedOriginsPattern) : null
  }

  @Override
  boolean isValidOrigin(String origin) {
    if (!origin) {
      return false
    }

    try {
      def uri = URI.create(origin)
      if (!(uri.scheme && uri.host)) {
        return false
      }

      if (allowedOrigins) {
        return allowedOrigins.matcher(origin).matches()
      }

      if (redirectHosts) {
        return redirectHosts.matcher(uri.host).matches()
      }

      if (!deckUri) {
        return false
      }

      return deckUri.scheme == uri.scheme && deckUri.host == uri.host && deckUri.port == uri.port
    } catch (URISyntaxException) {
      return false
    }
  }
}

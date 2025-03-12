/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.kork.crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.target.dynamic.AbstractRefreshableTargetSource;

/**
 * Implements a refreshable {@link X509Identity} using Spring AOP. This target source should be used
 * in a {@link org.springframework.aop.framework.ProxyFactory} to create a dynamic proxy for {@link
 * X509Identity}.
 *
 * @see X509IdentitySource#refreshable(Duration)
 */
@RequiredArgsConstructor
public class RefreshableX509Identity extends AbstractRefreshableTargetSource {
  private final X509IdentitySource identitySource;

  @Override
  protected X509Identity freshTarget() {
    try {
      return identitySource.load();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  protected boolean requiresRefresh() {
    Instant lastLoaded = identitySource.getLastLoaded();
    Instant lastModified = identitySource.getLastModified();
    Instant expiresAt = identitySource.getExpiresAt();
    return lastLoaded.isBefore(lastModified) || Instant.now().isAfter(expiresAt);
  }
}

/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.version;

import java.util.List;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Attempts to resolve the version of a service. */
@Slf4j
public class ServiceVersion {

  public static final String UNKNOWN_VERSION = "unknown";

  public static final String DEFAULT_VERSION = "0.0.0";

  private final List<VersionResolver> resolvers;
  private final ApplicationContext applicationContext;

  private String resolvedServiceVersion;

  @Autowired
  public ServiceVersion(ApplicationContext applicationContext, List<VersionResolver> resolvers) {
    this.applicationContext = applicationContext;
    this.resolvers = resolvers;
  }

  /**
   * Resolve the application version.
   *
   * <p>This call will never fail, although if the version cannot be resolved, "unknown" will be
   * returned.
   */
  @Nonnull
  public String resolve() {
    if (resolvedServiceVersion == null) {
      for (VersionResolver resolver : resolvers) {
        final String resolverName = resolver.getClass().getSimpleName();
        log.trace("Attempting to resolve service version: {}", resolverName);

        try {
          String version = resolver.resolve(applicationContext.getApplicationName());
          if (version != null) {
            resolvedServiceVersion = version;
            break;
          }
        } catch (Exception e) {
          log.error("Failed while resolving version: {}", resolverName, e);
        }
      }

      if (resolvedServiceVersion == null || resolvedServiceVersion.isEmpty()) {
        log.warn("Unable to determine the service version, setting it to unknown");
        resolvedServiceVersion = UNKNOWN_VERSION;
      }
    }

    return resolvedServiceVersion;
  }
}

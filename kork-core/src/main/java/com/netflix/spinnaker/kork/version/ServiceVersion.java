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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/** Attempts to resolve the version of a service. */
@Slf4j
public class ServiceVersion implements ApplicationContextAware {

  private static final String UNKNOWN_VERSION = "unknown";

  private final List<VersionResolver> resolvers;
  private ApplicationContext applicationContext;

  @Autowired
  public ServiceVersion(List<VersionResolver> resolvers) {
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
    if (applicationContext == null) {
      log.error("ApplicationContext has not been set: Cannot determine service version");
      return UNKNOWN_VERSION;
    }

    for (VersionResolver resolver : resolvers) {
      final String resolverName = resolver.getClass().getSimpleName();
      log.trace("Attempting to resolve service version: {}", resolverName);

      try {
        String version = resolver.resolve(applicationContext.getApplicationName());
        if (version != null) {
          return version;
        }
      } catch (Exception e) {
        log.error("Failed while resolving version: {}", resolverName, e);
      }
    }

    log.warn("Could not resolve service version");
    return UNKNOWN_VERSION;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}

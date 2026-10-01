/*
 * Copyright 2025 Harness, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public abstract class UserInputValidatedArtifactAccount implements ArtifactAccount {
  private final String name;

  /**
   * This is used in cases where user input is allowed for the URL. Specific github file references,
   * and simple http references. NOT all accounts need this as many define the access IN the
   * configuration file itself (e.g. dockerhub).
   */
  @Nullable private final UrlRestrictions urlRestrictions;

  /**
   * @param urlRestrictions restrictions for user-supplied URLs, or null for accounts whose URLs
   *     come only from their own configuration
   */
  protected UserInputValidatedArtifactAccount(
      String name, @Nullable UrlRestrictionsProperties urlRestrictions) {
    this.name = name;
    this.urlRestrictions = urlRestrictions == null ? null : urlRestrictions.toUrlRestrictions();
  }

  /** Account restrictions as configured, or the defaults when none are configured. */
  protected static UrlRestrictionsProperties orDefault(
      @Nullable UrlRestrictionsProperties urlRestrictions) {
    return urlRestrictions == null ? new UrlRestrictionsProperties() : urlRestrictions;
  }
}

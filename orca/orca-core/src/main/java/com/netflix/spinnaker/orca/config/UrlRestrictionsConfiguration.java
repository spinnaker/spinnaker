/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.orca.config;

import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Restrictions on user-supplied URLs (webhook stages, {@code #fromUrl} and friends). */
@Configuration
public class UrlRestrictionsConfiguration {

  public static final String PREFIX = "user-configured-url-restrictions";

  @Bean
  @ConfigurationProperties(PREFIX)
  public UrlRestrictionsProperties userConfiguredUrlRestrictionProperties() {
    return new UrlRestrictionsProperties();
  }

  @Bean
  @ConfigurationProperties(PREFIX + ".http-client-properties")
  public UserConfiguredHttpClientProperties userConfiguredHttpClientProperties() {
    return new UserConfiguredHttpClientProperties();
  }

  @Bean
  public UrlRestrictions userConfiguredUrlRestrictions(
      UrlRestrictionsProperties userConfiguredUrlRestrictionProperties) {
    return userConfiguredUrlRestrictionProperties.toUrlRestrictions();
  }
}

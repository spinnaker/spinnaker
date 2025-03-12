/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.fiat.config;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

/** Defines the configs for the various resource providers */
@Data
@Configuration
@ConfigurationProperties("resource.provider")
public class ResourceProviderConfig {

  /**
   * Configs for the {@link com.netflix.spinnaker.fiat.providers.DefaultApplicationResourceProvider}
   */
  @NestedConfigurationProperty
  private ApplicationProviderConfig application = new ApplicationProviderConfig();

  @Data
  public static class ApplicationProviderConfig {
    @NestedConfigurationProperty private ClouddriverConfig clouddriver = new ClouddriverConfig();

    /** Controls whether the application "details" are stored with the User Permission or not */
    private boolean suppressDetails;

    /**
     * Defines the set of keys to be excluded when suppressing application details Excluding
     * chaosMonkey by default since {@link
     * com.netflix.spinnaker.fiat.providers.ChaosMonkeyApplicationResourcePermissionSource} is the
     * only place where these details are accessed.
     */
    private Set<String> detailsExcludedFromSuppression = Sets.newHashSet("chaosMonkey");
  }

  @Data
  public static class ClouddriverConfig {
    /**
     * If true, then {@link com.netflix.spinnaker.fiat.providers.DefaultApplicationResourceProvider}
     * will call Clouddriver in addition to Front50 for loading applications. If false, then only
     * Front50 will be used.
     */
    private boolean loadApplications = true;

    /**
     * Defines how frequently the clouddriver application cache is refreshed. Note that the
     * clouddriver application cache is refreshed only when loadApplications: true
     */
    private long cacheRefreshIntervalMs = 30000;
  }
}

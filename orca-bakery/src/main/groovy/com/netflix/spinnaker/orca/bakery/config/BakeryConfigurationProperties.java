/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.config;

import com.netflix.spinnaker.kork.web.selector.v2.SelectableService;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bakery")
public class BakeryConfigurationProperties {
  private String baseUrl;
  private boolean roscoApisEnabled = false;
  private boolean extractBuildDetails = false;
  private boolean allowMissingPackageInstallation = false;
  private List<SelectableService.BaseUrl> baseUrls;

  // Temporary config that, if true, overrides the need for setting BakerySelector.SELECT_BAKERY in
  // stage.context
  // to enable bakery service selection.
  private boolean selectorEnabled = false;

  public String getBaseUrl() {
    return baseUrl;
  }

  public List<SelectableService.BaseUrl> getBaseUrls() {
    return baseUrls;
  }

  public boolean isRoscoApisEnabled() {
    return roscoApisEnabled;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void setRoscoApisEnabled(boolean roscoApisEnabled) {
    this.roscoApisEnabled = roscoApisEnabled;
  }

  public void setBaseUrls(List<SelectableService.BaseUrl> baseUrls) {
    this.baseUrls = baseUrls;
  }

  public boolean isExtractBuildDetails() {
    return extractBuildDetails;
  }

  public void setExtractBuildDetails(boolean extractBuildDetails) {
    this.extractBuildDetails = extractBuildDetails;
  }

  public boolean isAllowMissingPackageInstallation() {
    return allowMissingPackageInstallation;
  }

  public void setAllowMissingPackageInstallation(boolean allowMissingPackageInstallation) {
    this.allowMissingPackageInstallation = allowMissingPackageInstallation;
  }

  public boolean isSelectorEnabled() {
    return selectorEnabled;
  }

  public void setSelectorEnabled(boolean selectorEnabled) {
    this.selectorEnabled = selectorEnabled;
  }
}

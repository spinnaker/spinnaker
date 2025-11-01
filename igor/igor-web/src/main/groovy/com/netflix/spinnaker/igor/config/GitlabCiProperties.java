/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "gitlab-ci")
@Validated
public class GitlabCiProperties implements BuildServerProperties<GitlabCiProperties.GitlabCiHost> {
  private int cachedJobTTLDays = 60;

  @Valid private List<GitlabCiHost> masters = new ArrayList<>();

  public int getCachedJobTTLDays() {
    return cachedJobTTLDays;
  }

  public void setCachedJobTTLDays(int cachedJobTTLDays) {
    this.cachedJobTTLDays = cachedJobTTLDays;
  }

  public List<GitlabCiHost> getMasters() {
    return masters;
  }

  public void setMasters(List<GitlabCiHost> masters) {
    this.masters = masters;
  }

  public static class GitlabCiHost implements BuildServerProperties.Host {
    @NotEmpty private String name;
    @NotEmpty private String address;
    private String privateToken;
    private boolean limitByMembership = true;
    private boolean limitByOwnership = false;
    private Integer defaultHttpPageLength = 100;
    private Integer itemUpperThreshold = 1000;
    private Integer httpRetryMaxAttempts = 5;
    private Integer httpRetryWaitSeconds = 2;
    private Boolean httpRetryExponentialBackoff = false;
    private Permissions.Builder permissions = new Permissions.Builder();

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public String getPrivateToken() {
      return privateToken;
    }

    public void setPrivateToken(String privateToken) {
      this.privateToken = privateToken;
    }

    public boolean getLimitByMembership() {
      return limitByMembership;
    }

    public boolean isLimitByMembership() {
      return limitByMembership;
    }

    public void setLimitByMembership(boolean limitByMembership) {
      this.limitByMembership = limitByMembership;
    }

    public boolean getLimitByOwnership() {
      return limitByOwnership;
    }

    public boolean isLimitByOwnership() {
      return limitByOwnership;
    }

    public void setLimitByOwnership(boolean limitByOwnership) {
      this.limitByOwnership = limitByOwnership;
    }

    public Integer getItemUpperThreshold() {
      return itemUpperThreshold;
    }

    public void setItemUpperThreshold(Integer itemUpperThreshold) {
      this.itemUpperThreshold = itemUpperThreshold;
    }

    public Permissions.Builder getPermissions() {
      return permissions;
    }

    public void setPermissions(Permissions.Builder permissions) {
      this.permissions = permissions;
    }

    public Integer getHttpRetryWaitSeconds() {
      return httpRetryWaitSeconds;
    }

    public void setHttpRetryWaitSeconds(Integer httpRetryWaitSeconds) {
      this.httpRetryWaitSeconds = httpRetryWaitSeconds;
    }

    public Integer getHttpRetryMaxAttempts() {
      return httpRetryMaxAttempts;
    }

    public void setHttpRetryMaxAttempts(Integer httpRetryMaxAttempts) {
      this.httpRetryMaxAttempts = httpRetryMaxAttempts;
    }

    public Boolean getHttpRetryExponentialBackoff() {
      return httpRetryExponentialBackoff;
    }

    public void setHttpRetryExponentialBackoff(Boolean httpRetryExponentialBackoff) {
      this.httpRetryExponentialBackoff = httpRetryExponentialBackoff;
    }

    public Integer getDefaultHttpPageLength() {
      return defaultHttpPageLength;
    }

    public void setDefaultHttpPageLength(Integer defaultHttpPageLength) {
      if (defaultHttpPageLength == null
          || defaultHttpPageLength < 1
          || defaultHttpPageLength > 100) {
        throw new IllegalArgumentException(
            "Invalid Gitlab CI config.  defaultHttpPageLength must be a valid number between 1-100");
      }
      this.defaultHttpPageLength = defaultHttpPageLength;
    }
  }
}

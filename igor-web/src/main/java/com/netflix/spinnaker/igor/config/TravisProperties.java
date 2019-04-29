/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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
import java.util.List;
import javax.validation.Valid;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travis")
public class TravisProperties implements BuildServerProperties<TravisProperties.TravisHost> {
  private long newBuildGracePeriodSeconds = 10;
  private boolean repositorySyncEnabled = false;
  private int cachedJobTTLDays = 60;
  @Valid private List<TravisHost> masters;
  @Valid private List<String> regexes;
  /**
   * Lets you customize the build message used when Spinnaker triggers builds in Travis. If you set
   * a custom parameter in the Travis stage in Spinnaker with the value of this property as the key
   * (e.g <code>travis.buildMessage=My customized message</code>, the build message in Travis will
   * be <em>Triggered from Spinnaker: My customized message</em>. The first part of this message is
   * not customizable.
   */
  private String buildMessageKey = "travis.buildMessage";

  public boolean getRepositorySyncEnabled() {
    return repositorySyncEnabled;
  }

  public boolean isRepositorySyncEnabled() {
    return repositorySyncEnabled;
  }

  public void setRepositorySyncEnabled(boolean repositorySyncEnabled) {
    this.repositorySyncEnabled = repositorySyncEnabled;
  }

  public int getCachedJobTTLDays() {
    return cachedJobTTLDays;
  }

  public void setCachedJobTTLDays(int cachedJobTTLDays) {
    this.cachedJobTTLDays = cachedJobTTLDays;
  }

  public List<TravisHost> getMasters() {
    return masters;
  }

  public void setMasters(List<TravisHost> masters) {
    this.masters = masters;
  }

  public List<String> getRegexes() {
    return regexes;
  }

  public void setRegexes(List<String> regexes) {
    this.regexes = regexes;
  }

  public String getBuildMessageKey() {
    return buildMessageKey;
  }

  public void setBuildMessageKey(String buildMessageKey) {
    this.buildMessageKey = buildMessageKey;
  }

  public long getNewBuildGracePeriodSeconds() {
    return newBuildGracePeriodSeconds;
  }

  public void setNewBuildGracePeriodSeconds(long newBuildGracePeriodSeconds) {
    this.newBuildGracePeriodSeconds = newBuildGracePeriodSeconds;
  }

  public static class TravisHost implements BuildServerProperties.Host {
    @NotEmpty private String name;
    @NotEmpty private String baseUrl;
    @NotEmpty private String address;
    @NotEmpty private String githubToken;
    private int numberOfRepositories = 25;
    private Integer itemUpperThreshold;
    private Permissions.Builder permissions = new Permissions.Builder();

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public String getGithubToken() {
      return githubToken;
    }

    public void setGithubToken(String githubToken) {
      this.githubToken = githubToken;
    }

    public int getNumberOfRepositories() {
      return numberOfRepositories;
    }

    public void setNumberOfRepositories(int numberOfRepositories) {
      this.numberOfRepositories = numberOfRepositories;
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
  }
}

/*
 * Copyright 2017 bol.com
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

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Helper class to map masters in properties file into a validated property map. */
@ConditionalOnProperty("gitlab.base-url")
@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {
  @NotEmpty private String baseUrl;

  @NotEmpty private String privateToken;

  @NotNull private Integer commitDisplayLength;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getPrivateToken() {
    return privateToken;
  }

  public void setPrivateToken(String privateToken) {
    this.privateToken = privateToken;
  }

  public Integer getCommitDisplayLength() {
    return commitDisplayLength;
  }

  public void setCommitDisplayLength(Integer commitDisplayLength) {
    this.commitDisplayLength = commitDisplayLength;
  }
}

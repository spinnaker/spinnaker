/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import java.util.List;

public class AliCloudCredentials extends AbstractAccountCredentials<AccountCredentials> {

  private static final String CLOUD_PROVIDER = "alicloud";

  private String name;

  private String accessKeyId;

  private String accessSecretKey;

  private List<String> regions;

  private List<String> requiredGroupMembership;

  public void setName(String name) {
    this.name = name;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getAccessSecretKey() {
    return accessSecretKey;
  }

  public void setAccessSecretKey(String accessSecretKey) {
    this.accessSecretKey = accessSecretKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getEnvironment() {
    return null;
  }

  @Override
  public String getAccountType() {
    return null;
  }

  @Override
  @JsonIgnore
  public AccountCredentials getCredentials() {
    return null;
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  public List<String> getRegions() {
    return regions;
  }

  public void setRegions(List<String> regions) {
    this.regions = regions;
  }
}

/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.descriptions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;

public class DeleteEntityTagsDescription implements CredentialsNameable {
  @JsonIgnore
  private AccountCredentials credentials;

  @JsonProperty
  private String id;

  @JsonProperty
  private String account;

  public String getId() {
    return id;
  }

  @Override
  public String getAccount() {
    return account;
  }

  @Override
  @JsonIgnore
  public AccountCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(AccountCredentials accountCredentials) {
    this.credentials = accountCredentials;
  }
}

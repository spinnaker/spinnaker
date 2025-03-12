/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable;
import lombok.Data;

@Data
public abstract class AbstractCloudFoundryDescription implements AccountNameable {
  @JsonIgnore private CloudFoundryClient client;

  private String region;

  @JsonIgnore private CloudFoundryCredentials credentials;

  @Override
  public String getAccount() {
    if (credentials != null) {
      return credentials.getName();
    }
    throw new IllegalStateException("Credentials must not be null");
  }
}

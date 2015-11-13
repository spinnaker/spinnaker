/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spinnaker.kato.cf.security

import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import org.cloudfoundry.client.lib.CloudCredentials
/**
 * Capture {@link AccountCredentials} for a Cloud Foundry instance
 *
 *
 */
class CloudFoundryAccountCredentials implements AccountCredentials<CloudCredentials> {

  private static final String CLOUD_PROVIDER = "cf";

  String name
  String api
  String org
  String space
  String username
  String environment
  String accountType
  String password

  @Override
  CloudCredentials getCredentials() {
    new CloudCredentials(username, password)
  }

  @Override
  String getProvider() {
    getCloudProvider()
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }

  @Override
  List<String> getRequiredGroupMembership() {
    []
  }
}

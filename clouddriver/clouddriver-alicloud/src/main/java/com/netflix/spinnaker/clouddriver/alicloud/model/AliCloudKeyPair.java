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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.model.KeyPair;

public class AliCloudKeyPair implements KeyPair {

  String account;
  String region;
  String keyName;
  String keyFingerprint;
  String cloudProvider = AliCloudProvider.ID;

  public AliCloudKeyPair(String account, String region, String keyName, String keyFingerprint) {
    this.account = account;
    this.region = region;
    this.keyName = keyName;
    this.keyFingerprint = keyFingerprint;
  }

  @Override
  public String getKeyName() {
    return keyName;
  }

  @Override
  public String getKeyFingerprint() {
    return keyFingerprint;
  }

  public String getAccount() {
    return account;
  }

  public String getRegion() {
    return region;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }
}

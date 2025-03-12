/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.description;

import com.netflix.spinnaker.clouddriver.deploy.description.EnableDisableDescriptionTrait;
import java.util.Collection;
import java.util.Collections;

public class EnableDisableCloudrunDescription extends AbstractCloudrunCredentialsDescription
    implements EnableDisableDescriptionTrait {

  String accountName;
  String serverGroupName;
  String region;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  @Override
  public void setServerGroupName(String serverGroupName) {
    this.serverGroupName = serverGroupName;
  }

  @Override
  public Collection<String> getServerGroupNames() {
    return Collections.EMPTY_LIST;
  }

  public Integer getDesiredPercentage() {
    throw new IllegalArgumentException(
        "The selected provider hasn't implemented enabling/disabling by percentage yet");
  }

  public void setDesiredPercentage(Integer desiredPercentage) {
    throw new IllegalArgumentException(
        "The selected provider hasn't implemented enabling/disabling by percentage yet");
  }

  @Override
  public String getServerGroupName() {
    return this.serverGroupName;
  }
}

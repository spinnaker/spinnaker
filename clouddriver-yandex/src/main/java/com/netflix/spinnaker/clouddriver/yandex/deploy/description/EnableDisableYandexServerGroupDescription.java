/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.yandex.deploy.description;

import com.netflix.spinnaker.clouddriver.deploy.description.EnableDisableDescriptionTrait;
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Collection;
import java.util.Collections;
import lombok.Data;

/**
 * "Enabling" means adding a server group to the target pool of each of its network load balancers.
 *
 * <p>"Disabling" means removing a server group from the target pool of each of its network load
 * balancers.
 */
@Data
public class EnableDisableYandexServerGroupDescription
    implements CredentialsChangeable, ServerGroupsNameable, EnableDisableDescriptionTrait {
  private YandexCloudCredentials credentials;
  private String serverGroupName;

  @Override
  public Collection<String> getServerGroupNames() {
    return Collections.singletonList(serverGroupName);
  }

  @Override
  public Integer getDesiredPercentage() {
    throw new IllegalArgumentException(
        "Yandex cloud provider hasn't implemented enabling/disabling by percentage yet");
  }

  @Override
  public void setDesiredPercentage(Integer percentage) {
    throw new IllegalArgumentException(
        "Yandex cloud provider hasn't implemented enabling/disabling by percentage yet");
  }
}

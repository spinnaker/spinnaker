/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@RequiredArgsConstructor
@Component
public class CloudFoundryInstanceProvider implements InstanceProvider {
  private final CacheRepository repository;

  @Nullable
  @Override
  public Instance getInstance(String account, String region, String id) {
    return repository.findInstanceByKey(Keys.getInstanceKey(account, id)).orElse(null);
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  public final String getCloudProvider() {
    return CloudFoundryCloudProvider.ID;
  }
}

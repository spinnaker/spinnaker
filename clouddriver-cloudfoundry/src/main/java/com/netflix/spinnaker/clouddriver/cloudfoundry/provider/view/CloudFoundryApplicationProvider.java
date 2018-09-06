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

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.APPLICATIONS;

@RequiredArgsConstructor
@Component
public class CloudFoundryApplicationProvider implements ApplicationProvider {
  private final Cache cacheView;
  private final CacheRepository repository;

  @Override
  public Set<? extends Application> getApplications(boolean expand) {
    return repository.findApplicationsByKeys(cacheView.filterIdentifiers(APPLICATIONS.getNs(), Keys.getApplicationKey("*")),
      expand ? CacheRepository.Detail.NAMES_ONLY : CacheRepository.Detail.NONE);
  }

  @Nullable
  @Override
  public CloudFoundryApplication getApplication(String name) {
    return repository.findApplicationByKey(Keys.getApplicationKey(name), CacheRepository.Detail.NAMES_ONLY).orElse(null);
  }
}

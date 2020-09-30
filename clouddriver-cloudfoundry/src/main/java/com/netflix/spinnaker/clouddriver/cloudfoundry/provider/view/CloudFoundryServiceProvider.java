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
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.model.ServiceInstance;
import com.netflix.spinnaker.clouddriver.model.ServiceProvider;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryServiceProvider implements ServiceProvider {
  private final CredentialsRepository<CloudFoundryCredentials> credentialsRepository;

  @Autowired
  public CloudFoundryServiceProvider(
      CredentialsRepository<CloudFoundryCredentials> credentialsRepository) {
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  public Collection<CloudFoundryService> getServices(String account, String region) {
    CloudFoundryCredentials credentials = credentialsRepository.getOne(account);
    if (credentials == null) {
      return Collections.emptyList();
    }

    return credentials.getCredentials().getServiceInstances().findAllServicesByRegion(region);
  }

  @Override
  public ServiceInstance getServiceInstance(
      String account, String region, String serviceInstanceName) {
    CloudFoundryCredentials credentials = credentialsRepository.getOne(account);
    if (credentials == null) {
      return null;
    }

    return credentials
        .getCredentials()
        .getServiceInstances()
        .getServiceInstance(region, serviceInstanceName);
  }

  @Override
  public String getCloudProvider() {
    return CloudFoundryCloudProvider.ID;
  }
}

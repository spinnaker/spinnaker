/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.listeners;

import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import java.util.List;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
class ConfigurationRefreshListener implements ApplicationListener<RefreshScopeRefreshedEvent> {

  private final List<CredentialsInitializerSynchronizable> credentialsSynchronizers;

  public ConfigurationRefreshListener(
      List<CredentialsInitializerSynchronizable> credentialsSynchronizers) {
    this.credentialsSynchronizers = credentialsSynchronizers;
  }

  @Override
  public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
    credentialsSynchronizers.forEach(CredentialsInitializerSynchronizable::synchronize);
  }
}

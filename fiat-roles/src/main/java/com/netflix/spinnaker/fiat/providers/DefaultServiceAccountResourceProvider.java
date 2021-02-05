/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultServiceAccountResourceProvider extends BaseServiceAccountResourceProvider {

  private final Front50Service front50Service;

  public DefaultServiceAccountResourceProvider(
      Front50Service front50Service,
      Collection<ServiceAccountPredicateProvider> serviceAccountPredicateProviders) {
    super(serviceAccountPredicateProviders);
    this.front50Service = front50Service;
  }

  @Override
  protected Set<ServiceAccount> loadAll() throws ProviderException {
    try {
      return new HashSet<>(front50Service.getAllServiceAccounts());
    } catch (Exception e) {
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }
}

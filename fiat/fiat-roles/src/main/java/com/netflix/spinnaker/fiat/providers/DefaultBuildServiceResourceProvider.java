/*
 * Copyright 2019 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.fiat.providers;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.providers.internal.IgorService;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("services.igor.enabled")
public class DefaultBuildServiceResourceProvider extends BaseResourceProvider<BuildService>
    implements ResourceProvider<BuildService> {

  private final IgorService igorService;
  private final ResourcePermissionProvider<BuildService> permissionProvider;

  @Autowired
  public DefaultBuildServiceResourceProvider(
      IgorService igorService, ResourcePermissionProvider<BuildService> permissionProvider) {
    super();
    this.igorService = igorService;
    this.permissionProvider = permissionProvider;
  }

  @Override
  protected Set<BuildService> loadAll() throws ProviderException {
    try {
      List<BuildService> buildServices = igorService.getAllBuildServices();
      buildServices.forEach(
          buildService ->
              buildService.setPermissions(permissionProvider.getPermissions(buildService)));
      return ImmutableSet.copyOf(buildServices);
    } catch (RuntimeException e) {
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }
}

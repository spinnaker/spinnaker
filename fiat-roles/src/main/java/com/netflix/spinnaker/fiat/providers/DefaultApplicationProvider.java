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

import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import lombok.NonNull;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DefaultApplicationProvider extends BaseProvider implements ApplicationProvider {

  @Autowired
  private Front50Service front50Service;

  @Autowired
  private ClouddriverService clouddriverService;

  @Override
  public Set<Application> getAll() throws ProviderException {
    try {
      Map<String, Application> appByName = front50Service
          .getAllApplicationPermissions()
          .stream()
          .collect(Collectors.toMap(Application::getName,
                                    Function.identity()));
      success();

      clouddriverService
          .getApplications()
          .stream()
          .filter(app -> !appByName.containsKey(app.getName()))
          .forEach(app -> appByName.put(app.getName(), app));
      success();

      return new HashSet<>(appByName.values());
    } catch (RetrofitError re) {
      failure();
      throw new ProviderException(re);
    }
  }

  @Override
  public Set<Application> getAllRestricted(@NonNull Collection<Role> roles) throws ProviderException {
    val groupNames = roles.stream().map(Role::getName).collect(Collectors.toList());
    return getAll()
        .stream()
        .filter(application -> !Collections.disjoint(application.getRequiredGroupMembership(), groupNames))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<Application> getAllUnrestricted() throws ProviderException {
    return getAll()
        .stream()
        .filter(application -> application.getRequiredGroupMembership().isEmpty())
        .collect(Collectors.toSet());
  }
}

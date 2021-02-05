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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.FallbackPermissionsResolver;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class DefaultApplicationResourceProvider extends BaseResourceProvider<Application>
    implements ResourceProvider<Application> {

  private final Front50Service front50Service;
  private final ClouddriverService clouddriverService;
  private final ResourcePermissionProvider<Application> permissionProvider;
  private final FallbackPermissionsResolver executeFallbackPermissionsResolver;

  private final boolean allowAccessToUnknownApplications;

  public DefaultApplicationResourceProvider(
      Front50Service front50Service,
      ClouddriverService clouddriverService,
      ResourcePermissionProvider<Application> permissionProvider,
      FallbackPermissionsResolver executeFallbackPermissionsResolver,
      boolean allowAccessToUnknownApplications) {
    this.front50Service = front50Service;
    this.clouddriverService = clouddriverService;
    this.permissionProvider = permissionProvider;
    this.executeFallbackPermissionsResolver = executeFallbackPermissionsResolver;
    this.allowAccessToUnknownApplications = allowAccessToUnknownApplications;
  }

  @Override
  public Set<Application> getAllRestricted(String userId, Set<Role> userRoles, boolean isAdmin)
      throws ProviderException {
    return getAllApplications(userId, userRoles, isAdmin, true);
  }

  @Override
  public Set<Application> getAllUnrestricted() throws ProviderException {
    return getAllApplications(null, Collections.emptySet(), false, false);
  }

  @Override
  protected Set<Application> loadAll() throws ProviderException {
    try {
      List<Application> front50Applications = front50Service.getAllApplications();
      List<Application> clouddriverApplications = clouddriverService.getApplications();

      // Stream front50 first so that if there's a name collision, we'll keep that one instead of
      // the clouddriver application (since front50 might have permissions stored on it, but the
      // clouddriver version definitely won't)
      List<Application> applications =
          Streams.concat(front50Applications.stream(), clouddriverApplications.stream())
              .filter(distinctByKey(a -> a.getName().toUpperCase()))
              // Collect to a list instead of set since we're about to modify the applications
              .collect(toImmutableList());

      applications.forEach(
          application -> {
            Permissions permissions = permissionProvider.getPermissions(application);

            // Check to see if we need to fallback permissions to the configured fallback
            application.setPermissions(
                executeFallbackPermissionsResolver.shouldResolve(permissions)
                    ? executeFallbackPermissionsResolver.resolve(permissions)
                    : permissions);
          });

      if (allowAccessToUnknownApplications) {
        // no need to include applications w/o explicit permissions if we're allowing access to
        // unknown applications by default
        return applications.stream()
            .filter(a -> a.getPermissions().isRestricted())
            .collect(toImmutableSet());
      } else {
        return ImmutableSet.copyOf(applications);
      }
    } catch (RuntimeException e) {
      throw new ProviderException(this.getClass(), e);
    }
  }

  // Keeps only the first object with the key
  private static Predicate<Application> distinctByKey(Function<Application, String> keyExtractor) {
    Set<String> seenKeys = new HashSet<>();
    return t -> seenKeys.add(keyExtractor.apply(t));
  }

  private Set<Application> getAllApplications(
      String userId, Set<Role> userRoles, boolean isAdmin, boolean isRestricted) {
    if (allowAccessToUnknownApplications) {
      /*
       * By default, the `BaseProvider` parent methods will filter out any applications that the authenticated user does
       * not have access to.
       *
       * This is incompatible with `allowAccessToUnknownApplications` which implicitly grants access to any unknown (or
       * filtered) applications.
       *
       * In this case, it is appropriate to just return all applications and allow the subsequent authorization checks
       * to determine whether read, write or nothing should be granted.
       */
      return getAll();
    }

    return isRestricted
        ? super.getAllRestricted(userId, userRoles, isAdmin)
        : super.getAllUnrestricted();
  }
}

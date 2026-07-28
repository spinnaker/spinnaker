/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.igor.config.security;

import com.netflix.spinnaker.igor.config.GoogleCloudBuildProperties;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildService;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner-local {@link ResourceAclResolver} for Igor. Igor owns ACLs for exactly one resource type —
 * {@code build_service} — and resolves them straight from its own in-process registries, never from
 * a remote or cached copy of another service's data:
 *
 * <ul>
 *   <li>the {@link BuildServices} registry (Jenkins / Travis / GitLab / Concourse / Wercker hosts),
 *       and
 *   <li>the {@link GoogleCloudBuildProperties} GCB accounts, which register separately from {@link
 *       BuildServices}.
 * </ul>
 *
 * Each build service carries its embedded {@link Permissions} ACL (the {@link BuildService} {@code
 * ProtectedResource} accessor). Igor deals strictly in build services / accounts and has no notion
 * of the {@code application} resource; any other resource type resolves to {@code null}.
 *
 * <p>Returning {@code null} signals "not a build service Igor owns" (unknown/absent), which the
 * enclosing {@link IgorPermissionEvaluator} handles via {@code
 * authz.igor.allow-access-to-unknown-build-services}.
 */
public class IgorResourceAclResolver implements ResourceAclResolver {

  private static final Logger log = LoggerFactory.getLogger(IgorResourceAclResolver.class);

  private final BuildServices buildServices;
  private final Optional<GoogleCloudBuildProperties> googleCloudBuildProperties;

  public IgorResourceAclResolver(
      BuildServices buildServices,
      Optional<GoogleCloudBuildProperties> googleCloudBuildProperties) {
    this.buildServices = buildServices;
    this.googleCloudBuildProperties = googleCloudBuildProperties;
  }

  @Nullable
  @Override
  public Permissions resolve(ResourceType resourceType, String resourceName) {
    if (resourceName == null || !ResourceType.BUILD_SERVICE.equals(resourceType)) {
      return null;
    }

    BuildOperations service = buildServices.getService(resourceName);
    if (service != null) {
      return service.getPermissions();
    }

    if (googleCloudBuildProperties.isPresent()
        && googleCloudBuildProperties.get().getAccounts() != null) {
      for (BuildService gcbAccount : googleCloudBuildProperties.get().getGcbBuildServices()) {
        if (resourceName.equals(gcbAccount.getName())) {
          return gcbAccount.getPermissions();
        }
      }
    }

    log.debug("No owner-local ACL for build service '{}'", resourceName);
    return null;
  }
}

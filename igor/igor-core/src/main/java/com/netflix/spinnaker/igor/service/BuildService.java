/*
 * Copyright 2016 Schibsted ASA.
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
package com.netflix.spinnaker.igor.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ProtectedResource;
import com.netflix.spinnaker.security.authz.ResourceType;

/**
 * Interface representing a Build Service host (CI) and the permissions needed to access it. Most
 * implementations should implement the {@link BuildOperations} interface instead.
 *
 * <p>A build service is the only resource type Igor owns ACLs for. It implements {@link
 * ProtectedResource} so the owner-local {@code PolicyDecisionPointPermissionEvaluator} can derive
 * the resource type, name and embedded ACL straight from the in-process build-service object,
 * without any remote lookup.
 */
public interface BuildService extends ProtectedResource {
  /**
   * Get the name of the build service host
   *
   * @return The name of the build service
   */
  String getName();

  /**
   * Get the type of the build service
   *
   * @return The type of the build service
   */
  BuildServiceProvider getBuildServiceProvider();

  /**
   * Get the permissions of the build service. Read permissions are needed to be able to interact
   * with the build service host and use it as a trigger in Spinnaker. Write permissions are needed
   * to trigger CI builds/jobs from a Spinnaker pipeline.
   *
   * @return The permissions needed to access this build service host
   */
  Permissions getPermissions();

  /** Igor only ever authorizes {@code build_service} resources. */
  @JsonIgnore
  @Override
  default ResourceType getResourceType() {
    return ResourceType.BUILD_SERVICE;
  }

  @JsonIgnore
  default BuildServiceView getView() {
    return new BuildServiceView(this);
  }

  class BuildServiceView implements BuildService {
    final String name;
    final BuildServiceProvider buildServiceProvider;
    final Permissions permissions;

    private BuildServiceView(BuildService buildService) {
      this.name = buildService.getName();
      this.buildServiceProvider = buildService.getBuildServiceProvider();
      this.permissions = buildService.getPermissions();
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public BuildServiceProvider getBuildServiceProvider() {
      return buildServiceProvider;
    }

    @Override
    public Permissions getPermissions() {
      return permissions;
    }
  }
}

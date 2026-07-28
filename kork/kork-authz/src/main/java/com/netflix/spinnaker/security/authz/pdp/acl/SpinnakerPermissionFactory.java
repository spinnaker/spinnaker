/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.authz.pdp.acl;

import com.netflix.spinnaker.security.authz.Authorization;
import org.springframework.security.acls.domain.DefaultPermissionFactory;
import org.springframework.security.acls.model.Permission;

/**
 * {@link DefaultPermissionFactory} that also knows how to build Spinnaker's custom EXECUTE
 * permission alongside the standard {@code BasePermission} bits. This lets {@code
 * AclPermissionEvaluator} resolve a permission passed by name (e.g. {@code "EXECUTE"}).
 */
public class SpinnakerPermissionFactory extends DefaultPermissionFactory {

  public SpinnakerPermissionFactory() {
    super();
    registerPermission(SpinnakerPermission.EXECUTE, Authorization.EXECUTE.name());
  }

  /** Maps a Spinnaker {@link Authorization} to its Spring ACL {@link Permission} bit. */
  public static Permission toPermission(Authorization authorization) {
    switch (authorization) {
      case READ:
        return SpinnakerPermission.READ;
      case WRITE:
        return SpinnakerPermission.WRITE;
      case CREATE:
        return SpinnakerPermission.CREATE;
      case EXECUTE:
        return SpinnakerPermission.EXECUTE;
      default:
        throw new IllegalArgumentException("Unsupported authorization: " + authorization);
    }
  }
}

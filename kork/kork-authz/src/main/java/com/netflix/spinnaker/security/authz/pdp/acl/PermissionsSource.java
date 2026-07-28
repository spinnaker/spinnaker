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

import com.netflix.spinnaker.security.authz.Permissions;
import javax.annotation.Nullable;
import org.springframework.security.acls.model.ObjectIdentity;

/**
 * Supplies the embedded {@link Permissions} ACL for a resource identified by its {@link
 * ObjectIdentity}. Owning services implement this against their own in-process domain model
 * (Front50 applications, Clouddriver accounts, Igor build services) so no ACLs are replicated
 * across services.
 */
@FunctionalInterface
public interface PermissionsSource {

  /**
   * @return the resource's embedded ACL, or {@code null} if the resource is unknown to this source
   */
  @Nullable
  Permissions getPermissions(ObjectIdentity objectIdentity);
}

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

import org.springframework.security.acls.domain.AbstractPermission;
import org.springframework.security.acls.domain.BasePermission;
import org.springframework.security.acls.model.Permission;

/**
 * Spring ACL {@link Permission} bits for Spinnaker authorizations. READ/WRITE/CREATE reuse {@link
 * BasePermission}; Spinnaker's EXECUTE has no {@code BasePermission} equivalent so it is defined
 * here as a custom permission bit (mask {@code 1 << 5}, code {@code 'E'}).
 */
public class SpinnakerPermission extends AbstractPermission {

  /** Custom EXECUTE permission bit (BasePermission only defines READ/WRITE/CREATE/DELETE/ADMIN). */
  public static final Permission EXECUTE = new SpinnakerPermission(1 << 5, 'E');

  /** READ maps to the standard {@link BasePermission#READ}. */
  public static final Permission READ = BasePermission.READ;

  /** WRITE maps to the standard {@link BasePermission#WRITE}. */
  public static final Permission WRITE = BasePermission.WRITE;

  /** CREATE maps to the standard {@link BasePermission#CREATE}. */
  public static final Permission CREATE = BasePermission.CREATE;

  protected SpinnakerPermission(int mask) {
    super(mask);
  }

  protected SpinnakerPermission(int mask, char code) {
    super(mask, code);
  }
}

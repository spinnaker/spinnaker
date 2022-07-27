/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.security;

import org.springframework.security.core.Authentication;

/**
 * An AccessControlled object is an object that knows its own permissions and can check them against
 * a given user and authorization. This allows resources to support access control checks via Spring
 * Security against the resource object directly.
 */
public interface AccessControlled {
  /**
   * Checks if the authenticated user has a particular authorization on this object. Note that
   * checking if the user is an admin should be performed by a {@link
   * org.springframework.security.access.PermissionEvaluator} rather than in these domain objects.
   */
  boolean isAuthorized(Authentication authentication, Object authorization);
}

/*
 * Copyright 2022 Apple, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.security.AccessControlled;
import java.util.Objects;
import org.springframework.security.core.Authentication;

public class AccessControlledResource implements AccessControlled {
  private final Permissions permissions;

  public AccessControlledResource(Permissions permissions) {
    this.permissions = Objects.requireNonNull(permissions);
  }

  @Override
  public boolean isAuthorized(Authentication authentication, Object authorization) {
    Authorization a = Authorization.parse(authorization);
    return permissions.getAuthorizations(authentication.getAuthorities()).contains(a);
  }
}

/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.security.roles;

import com.netflix.spinnaker.security.authz.Role;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A user with roles asserted by the authentication mechanism (e.g. a SAML assertion that carries
 * group membership) prior to role-provider resolution.
 */
@Data
@Accessors(chain = true)
public class ExternalUser {
  private String id;
  private List<Role> externalRoles = new ArrayList<>();
}

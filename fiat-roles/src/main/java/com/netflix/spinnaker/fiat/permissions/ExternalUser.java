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

package com.netflix.spinnaker.fiat.permissions;

import com.netflix.spinnaker.fiat.model.resources.Role;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * ExternalUser is a model object for a user with roles assigned outside of the UserRoleProvider
 * configured within Fiat. The most common scenario is a SAML authentication assertion that also
 * includes role/group membership.
 */
@Data
public class ExternalUser {
  private String id;
  private List<Role> externalRoles = new ArrayList<>();
}

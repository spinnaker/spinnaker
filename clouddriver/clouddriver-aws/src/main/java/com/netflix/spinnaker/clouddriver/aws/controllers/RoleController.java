/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.controllers;

import com.netflix.spinnaker.clouddriver.aws.model.Role;
import com.netflix.spinnaker.clouddriver.aws.model.RoleProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
public class RoleController {

  @Autowired(required = false)
  List<RoleProvider> roleProviders;

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}")
  Collection<Role> getRoles(@PathVariable String cloudProvider) {
    if (roleProviders == null) {
      return Collections.emptyList();
    }

    Set<Role> roles =
        roleProviders.stream()
            .filter(roleProvider -> roleProvider.getCloudProvider().equals(cloudProvider))
            .flatMap(roleProvider -> roleProvider.getAll().stream())
            .collect(Collectors.toSet());

    return roles;
  }
}

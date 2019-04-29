/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.fiat.roles.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "file")
public class FileBasedUserRolesProvider implements UserRolesProvider {

  @Autowired ConfigProps configProps;

  private Map<String, List<Role>> parse() throws IOException {
    return parse(new BufferedReader(new FileReader(new File(configProps.getPath()))));
  }

  private Map<String, List<Role>> parse(Reader source) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(source, UserRolesMapping.class).toMap();
  }

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    try {
      return new ArrayList<>(parse().get(user.getId()));
    } catch (IOException io) {
      log.error("Couldn't load roles for user " + user.getId() + " from file", io);
    }
    return Collections.emptyList();
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    try {
      Collection<String> userIds =
          users.stream().map(ExternalUser::getId).collect(Collectors.toList());
      return parse().entrySet().stream()
          .filter(e -> userIds.contains(e.getKey()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    } catch (IOException io) {
      log.error("Couldn't mulitLoad roles from file", io);
    }
    return Collections.emptyMap();
  }

  @Data
  @Configuration
  @ConfigurationProperties(prefix = "auth.group-membership.file")
  static class ConfigProps {
    String path;
  }

  @Data
  static class UserRolesMapping {
    List<UserRoles> users;

    Map<String, List<Role>> toMap() {
      return users.stream().collect(Collectors.toMap(UserRoles::getUsername, UserRoles::getRoles));
    }
  }

  @Data
  static class UserRoles {
    String username;
    List<Role> roles;

    public List<Role> getRoles() {
      if (roles == null) {
        return Collections.emptyList();
      }
      return roles.stream()
          .map(r -> new Role(r.getName()).setSource(Role.Source.FILE))
          .collect(Collectors.toList());
    }
  }
}

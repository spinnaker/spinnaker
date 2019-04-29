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

package com.netflix.spinnaker.fiat.roles.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.DirectoryScopes;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Groups;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "google")
public class GoogleDirectoryUserRolesProvider implements UserRolesProvider, InitializingBean {

  @Autowired @Setter private Config config;

  private static final Collection<String> SERVICE_ACCOUNT_SCOPES =
      Collections.singleton(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY);

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(config.getDomain() != null, "Supply a domain");
    Assert.state(config.getAdminUsername() != null, "Supply an admin username");
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  private class GroupBatchCallback extends JsonBatchCallback<Groups> {

    Map<String, Collection<Role>> emailGroupsMap;

    String email;

    @Override
    public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.warn("Failed to fetch groups for user " + email + ": " + e.getMessage());
    }

    @Override
    public void onSuccess(Groups groups, HttpHeaders responseHeaders) throws IOException {
      if (groups == null || groups.getGroups() == null || groups.getGroups().isEmpty()) {
        log.debug("No groups found for user " + email);
        return;
      }

      Set<Role> groupSet =
          groups.getGroups().stream().flatMap(toRoleFn()).collect(Collectors.toSet());
      emailGroupsMap.put(email, groupSet);
    }
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (users == null || users.isEmpty()) {
      return new HashMap<>();
    }

    Collection<String> userEmails =
        users.stream().map(ExternalUser::getId).collect(Collectors.toList());
    HashMap<String, Collection<Role>> emailGroupsMap = new HashMap<>();
    Directory service = getDirectoryService();
    BatchRequest batch = service.batch();
    userEmails.forEach(
        email -> {
          try {
            GroupBatchCallback callback =
                new GroupBatchCallback().setEmailGroupsMap(emailGroupsMap).setEmail(email);
            HttpRequest request =
                service
                    .groups()
                    .list()
                    .setDomain(config.getDomain())
                    .setUserKey(email)
                    .buildHttpRequest();
            HttpBackOffUnsuccessfulResponseHandler handler =
                new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff());
            handler.setBackOffRequired(
                response -> {
                  int code = response.getStatusCode();
                  // 403 is Google's Rate limit exceeded response.
                  return code == 403 || code / 100 == 5;
                });
            request.setUnsuccessfulResponseHandler(handler);
            batch.queue(request, Groups.class, GoogleJsonErrorContainer.class, callback);

          } catch (IOException ioe) {
            throw new RuntimeException(ioe);
          }
        });

    try {
      batch.execute();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }

    return emailGroupsMap;
  }

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    if (user == null || user.getId().isEmpty()) {
      return new ArrayList<>();
    }

    String userEmail = user.getId();

    try {
      Groups groups = getGroupsFromEmail(userEmail);
      if (groups == null || groups.getGroups() == null || groups.getGroups().isEmpty()) {
        return new ArrayList<>();
      }

      return groups.getGroups().stream().flatMap(toRoleFn()).collect(Collectors.toList());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  protected Groups getGroupsFromEmail(String email) throws IOException {
    Directory service = getDirectoryService();
    return service.groups().list().setDomain(config.getDomain()).setUserKey(email).execute();
  }

  private GoogleCredential getGoogleCredential() {
    try {
      if (StringUtils.isNotEmpty(config.getCredentialPath())) {
        return GoogleCredential.fromStream(new FileInputStream(config.getCredentialPath()));
      } else {
        return GoogleCredential.getApplicationDefault();
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  private Directory getDirectoryService() {
    HttpTransport httpTransport = new NetHttpTransport();
    JacksonFactory jacksonFactory = new JacksonFactory();
    GoogleCredential credential = getGoogleCredential();

    PropertyAccessor accessor = PropertyAccessorFactory.forDirectFieldAccess(credential);
    accessor.setPropertyValue("serviceAccountUser", config.getAdminUsername());
    accessor.setPropertyValue("serviceAccountScopes", SERVICE_ACCOUNT_SCOPES);

    return new Directory.Builder(httpTransport, jacksonFactory, credential)
        .setApplicationName("Spinnaker-Fiat")
        .build();
  }

  private static Role toRole(Group g, Config.RoleSource src) {
    if (src == Config.RoleSource.EMAIL) {
      if (g.getEmail() == null) {
        return null;
      }
      return new Role().setName(g.getEmail().toLowerCase()).setSource(Role.Source.GOOGLE_GROUPS);
    } else if (src == Config.RoleSource.NAME) {
      if (g.getName() == null) {
        return null;
      }
      return new Role().setName(g.getName().toLowerCase()).setSource(Role.Source.GOOGLE_GROUPS);
    } else {
      throw new RuntimeException("Unexpected Google role source: " + src);
    }
  }

  private Function<Group, Stream<Role>> toRoleFn() {
    return (g) ->
        Arrays.stream(config.roleSources).map((r) -> GoogleDirectoryUserRolesProvider.toRole(g, r));
  }

  @Data
  @Configuration
  @ConfigurationProperties("auth.group-membership.google")
  public static class Config {

    /** Path to json credential file for the groups service account. */
    private String credentialPath;

    /** Email of the Google Apps admin the service account is acting on behalf of. */
    private String adminUsername;

    /** Google Apps for Work domain, e.g. netflix.com */
    private String domain;

    /**
     * List of sources to derive role name from group metadata, this setting is additive to allow
     * backwards compatibility
     */
    private RoleSource[] roleSources = new RoleSource[] {Config.RoleSource.NAME};

    /** RoleSource maps to metadata on the Group metadata, NAME = Group Name, Email = Group Email */
    private enum RoleSource {
      NAME,
      EMAIL
    }
  }
}

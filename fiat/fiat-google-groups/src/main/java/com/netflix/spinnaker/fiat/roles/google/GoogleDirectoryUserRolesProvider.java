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

import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "google")
public class GoogleDirectoryUserRolesProvider implements UserRolesProvider, InitializingBean {
  // Used to denote a pipeline permission, or manually created service account
  public static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";
  // Used to denote a shared service account, used by service account shared between pipelines by
  // unique role
  public static final String SHARED_SERVICE_ACCOUNT_SUFFIX = "@shared-managed-service-account";
  // https://docs.cloud.google.com/java/docs/reference/google-http-client/latest/com.google.api.client.http.javanet.NetHttpTransport  - use a single globally shared one of these.  AKA
  // DO NOT recreate per run.
  private static final HttpTransport httpTransport = new NetHttpTransport();

  @Autowired @Setter private Config config;

  private static final Collection<String> SERVICE_ACCOUNT_SCOPES =
      Collections.singleton(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY);

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(config.getDomain() != null, "Supply a domain");
    Assert.state(config.getAdminUsername() != null, "Supply an admin username");
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (users == null || users.isEmpty()) {
      return new HashMap<>();
    }

    Collection<String> userEmails = users.stream().map(ExternalUser::getId).toList();
    HashMap<String, Collection<Role>> emailGroupsMap = new HashMap<>();
    // Per some discussion... the google batch APIs have challenges.  Eg. they do an
    // "all-or-nothing" type operation
    // and may randomly fail.  So we use a custom thread pool to parallelize the requests and use a
    // backoff for EACH
    // request.  See
    // https://stackoverflow.com/a/78388542
    // and
    // https://docs.cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.batch
    // AS A RESULT:  THIS WILL run slower than a batched operation which does it all at once... WITH
    // upside this should be "safer"
    ForkJoinPool customPool = new ForkJoinPool(config.groupLookupConcurrency);
    try {
      log.info("Fetching group information for " + userEmails.size() + " users");
      customPool
          .submit(
              () ->
                  userEmails.parallelStream()
                      .forEach(
                          email -> {
                            log.debug("Getting roles for email " + email);
                            try {
                              emailGroupsMap.put(email, getRolesForEmail(email));
                            } catch (IOException ioe) {
                              log.warn(
                                  "Failed to fetch groups for user "
                                      + email
                                      + ": "
                                      + ioe.getMessage());
                            }
                          }))
          .get();
      log.info(
          "Actually got "
              + emailGroupsMap.size()
              + " sets of groups - which excludes any service accounts");
    } catch (Exception ioe) {
      throw new RuntimeException(ioe);
    } finally {
      customPool.shutdown();
    }

    return emailGroupsMap;
  }

  private Collection<Role> getRolesForEmail(String email) throws IOException {
    // Check if this is a managed service account, we should never check google groups for
    // these
    if (email.endsWith(SERVICE_ACCOUNT_SUFFIX) || email.endsWith(SHARED_SERVICE_ACCOUNT_SUFFIX)) {
      // Skip over this in the batch
      return new HashSet<>();
    }
    log.debug("Fetching group information for " + email);
    return getGroupsFromEmailRecursively(email).getGroups().stream()
        .flatMap(toRoleFn())
        .collect(Collectors.toSet());
  }

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    if (user == null || user.getId().isEmpty()) {
      return new ArrayList<>();
    }

    String userEmail = user.getId();
    // Check if this is a managed service account, we should never check google groups for these
    if (userEmail.endsWith(SERVICE_ACCOUNT_SUFFIX)
        || userEmail.endsWith(SHARED_SERVICE_ACCOUNT_SUFFIX)) {
      return new ArrayList<>();
    }

    try {
      Groups groups = getGroupsFromEmailRecursively(userEmail);
      if (groups == null || groups.getGroups() == null || groups.getGroups().isEmpty()) {
        return new ArrayList<>();
      }

      return groups.getGroups().stream().flatMap(toRoleFn()).collect(Collectors.toList());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * Retrieves all Google Groups associated with a given email address, including both direct and
   * indirect group memberships, if configured to do so.
   *
   * <p>This method first fetches the groups the user is directly a member of via {@link
   * #getGroupsFromEmail(String)}. If the configuration allows expanding indirect groups (i.e.,
   * nested groups), it recursively traverses each group's membership to collect nested groups.
   *
   * <p>The method avoids cycles and duplicate group processing by maintaining a set of already
   * collected group emails.
   *
   * @param email The email address whose group memberships should be retrieved.
   * @return A {@link Groups} object containing all the direct and (optionally) indirect group
   *     memberships.
   * @throws IOException If an error occurs while retrieving group information.
   */
  @VisibleForTesting
  protected Groups getGroupsFromEmailRecursively(String email) throws IOException {
    log.debug("Starting group fetch");
    final Groups groups = getGroupsFromEmail(email);
    log.debug("Completed fetching groups... found " + groups.size());
    if (groups == null
        || groups.getGroups() == null
        || groups.getGroups().isEmpty()
        || !config.isExpandIndirectGroups()) {
      return groups;
    }
    final Set<String> collectedGroup = new HashSet<>();
    final Deque<String> stack = new ArrayDeque<>();
    for (Group g : groups.getGroups()) {
      // Skip the group recursion IF no email is there....
      if (ObjectUtils.isEmpty(g.getEmail())) {
        continue;
      }
      stack.push(g.getEmail());
      collectedGroup.add(g.getEmail());
    }
    log.debug("Starting group recursion");
    while (!stack.isEmpty()) {
      String nextEmail = stack.pop();
      Groups subGroups = getGroupsFromEmail(nextEmail);
      if (subGroups == null || subGroups.getGroups() == null || subGroups.getGroups().isEmpty()) {
        log.debug(
            "No groups found for email " + nextEmail + " while looking for recursive sub groups");
        continue;
      }
      log.debug("Found groups for email " + nextEmail + " of " + subGroups.getGroups());
      for (Group g : subGroups.getGroups()) {
        if (ObjectUtils.isEmpty(g.getEmail())) {
          groups.getGroups().add(g);
          continue;
        }
        if (collectedGroup.contains(g.getEmail())) {
          continue;
        }
        stack.push(g.getEmail());
        groups.getGroups().add(g);
        collectedGroup.add(g.getEmail());
      }
    }
    log.debug("Completed group recursion");
    return groups;
  }

  protected Groups getGroupsFromEmail(String email) throws IOException {
    final Directory service = getDirectoryService();

    final Groups groups =
        service
            .groups()
            .list()
            .setDomain(config.getDomain())
            .setUserKey(email)
            .buildHttpRequest()
            .setUnsuccessfulResponseHandler(getGCPBackoffHandlerForGoogleApiRateLImitedCalls())
            .execute()
            .parseAs(Groups.class);

    String nextPageToken = groups.getNextPageToken();
    while (nextPageToken != null) {

      final Groups nextPage =
          service
              .groups()
              .list()
              .setDomain(config.getDomain())
              .setUserKey(email)
              .setPageToken(nextPageToken)
              .buildHttpRequest()
              .setUnsuccessfulResponseHandler(getGCPBackoffHandlerForGoogleApiRateLImitedCalls())
              .execute()
              .parseAs(Groups.class);
      groups.getGroups().addAll(nextPage.getGroups());
      nextPageToken = nextPage.getNextPageToken();
    }
    return groups;
  }

  public static HttpBackOffUnsuccessfulResponseHandler
      getGCPBackoffHandlerForGoogleApiRateLImitedCalls() {
    HttpBackOffUnsuccessfulResponseHandler handler =
        new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff());
    handler.setBackOffRequired(
        response -> {
          int code = response.getStatusCode();
          // 403 is Google's Rate limit exceeded response.
          return code == 403 || code == 429 || code / 100 == 5;
        });
    return handler;
  }

  private GoogleCredentials getGoogleCredential() {
    try {
      if (StringUtils.isNotEmpty(config.getCredentialPath())
          && StringUtils.isNotEmpty(config.getAdminUsername())) {
        return ServiceAccountCredentials.fromStream(new FileInputStream(config.getCredentialPath()))
            .createScoped(SERVICE_ACCOUNT_SCOPES) // add other scopes as needed
            .createDelegated(config.getAdminUsername());
      } else {
        return GoogleCredentials.getApplicationDefault();
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  private Directory getDirectoryService() {
    GsonFactory jacksonFactory = new GsonFactory();
    GoogleCredentials credentials = getGoogleCredential();
    return new Directory.Builder(
            httpTransport, jacksonFactory, new HttpCredentialsAdapter(credentials))
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

    /** expand indirect groups for emails */
    private boolean expandIndirectGroups = false;

    /**
     * List of sources to derive role name from group metadata, this setting is additive to allow
     * backwards compatibility
     */
    private RoleSource[] roleSources = new RoleSource[] {Config.RoleSource.NAME};

    private int groupLookupConcurrency = 10;

    /** RoleSource maps to metadata on the Group metadata, NAME = Group Name, Email = Group Email */
    private enum RoleSource {
      NAME,
      EMAIL
    }
  }
}

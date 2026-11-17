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
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.api.services.cloudidentity.v1.CloudIdentityScopes;
import com.google.api.services.cloudidentity.v1.model.GroupRelation;
import com.google.api.services.cloudidentity.v1.model.SearchTransitiveGroupsResponse;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import com.netflix.spinnaker.kork.google.auth.GoogleAuthProperties;
import com.netflix.spinnaker.kork.google.auth.GoogleCredentialsFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
  // Used to denote a pipeline permission, or manually created service account
  public static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";
  // Used to denote a shared service account, used by service account shared between pipelines by
  // unique role
  public static final String SHARED_SERVICE_ACCOUNT_SUFFIX = "@shared-managed-service-account";
  // https://docs.cloud.google.com/java/docs/reference/google-http-client/latest/com.google.api.client.http.javanet.NetHttpTransport  - use a single globally shared one of these.  AKA
  // DO NOT recreate per run.
  private static final HttpTransport httpTransport = new NetHttpTransport();

  @Autowired @Setter private Config config;

  @Setter private GoogleCredentialsFactory credentialsFactory = new GoogleCredentialsFactory();

  // Reuse credentials across group lookups so the SDK can cache and refresh impersonated tokens.
  private GoogleCredentials googleCredentials;

  private static final String DIRECTORY_SCOPE = DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY;

  private static final String CLOUD_IDENTITY_SCOPE =
      CloudIdentityScopes.CLOUD_IDENTITY_GROUPS_READONLY;

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.state(config.getDomain() != null, "Supply a domain");
    Assert.notNull(config.getAuth(), "auth must not be null");
    config.getAuth().validate();
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (users == null || users.isEmpty()) {
      return new HashMap<>();
    }

    Collection<String> userEmails = users.stream().map(ExternalUser::getId).toList();
    Map<String, Collection<Role>> emailGroupsMap = new ConcurrentHashMap<>();
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

  // TODO:  Move the directory stuff to a mock object vs. spying on the code methods. that'd provide
  // better tests
  @VisibleForTesting
  protected Collection<Role> getRolesForEmail(String email) throws IOException {
    // Check if this is a managed service account, we should never check google groups for
    // these
    if (email.endsWith(SERVICE_ACCOUNT_SUFFIX) || email.endsWith(SHARED_SERVICE_ACCOUNT_SUFFIX)) {
      // Skip over this in the batch
      return new HashSet<>();
    }
    log.debug("Fetching group information for " + email);

    Groups groups = getGroupsFromEmailRecursively(email);
    if (groups == null || groups.getGroups() == null || groups.getGroups().isEmpty()) {
      return new ArrayList<>();
    }
    return groups.getGroups().stream().flatMap(toRoleFn()).collect(Collectors.toSet());
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
   * <p>When {@code expandIndirectGroups} is enabled, this method uses the Cloud Identity {@code
   * searchTransitiveGroups} API to fetch all transitive memberships in a single paginated call,
   * instead of manually traversing nested groups.
   *
   * <p>When {@code expandIndirectGroups} is disabled, only direct group memberships are returned
   * via the Admin Directory API.
   *
   * @param email The email address whose group memberships should be retrieved.
   * @return A {@link Groups} object containing all the direct and (optionally) indirect group
   *     memberships.
   * @throws IOException If an error occurs while retrieving group information.
   */
  @VisibleForTesting
  protected Groups getGroupsFromEmailRecursively(String email) throws IOException {
    if (!config.isExpandIndirectGroups()) {
      log.debug("expandIndirectGroups is disabled, fetching direct groups only for {}", email);
      return getGroupsFromEmail(email);
    }

    log.debug("Fetching transitive groups via Cloud Identity API for {}", email);
    return getTransitiveGroupsFromEmail(email);
  }

  /**
   * Uses the Cloud Identity {@code groups.memberships.searchTransitiveGroups} API to retrieve all
   * direct and indirect group memberships for the given email in a single paginated call.
   *
   * @param email The email address whose transitive group memberships should be retrieved.
   * @return A {@link Groups} object containing all transitive group memberships.
   * @throws IOException If an error occurs while retrieving group information.
   */
  @VisibleForTesting
  protected Groups getTransitiveGroupsFromEmail(String email) throws IOException {
    final CloudIdentity service = getCloudIdentityService();
    final String query = "member_key_id == '" + email + "'";
    final List<Group> allGroups = new ArrayList<>();
    String nextPageToken = null;

    do {
      SearchTransitiveGroupsResponse response =
          service
              .groups()
              .memberships()
              .searchTransitiveGroups("groups/-")
              .setQuery(query)
              .setPageToken(nextPageToken)
              .buildHttpRequest()
              .setUnsuccessfulResponseHandler(getGCPBackoffHandlerForGoogleApiRateLImitedCalls())
              .execute()
              .parseAs(SearchTransitiveGroupsResponse.class);

      if (response.getMemberships() != null) {
        for (GroupRelation relation : response.getMemberships()) {
          Group group = new Group();
          if (relation.getGroupKey() != null) {
            group.setEmail(relation.getGroupKey().getId());
          }
          if (relation.getDisplayName() != null) {
            group.setName(relation.getDisplayName());
          }
          allGroups.add(group);
        }
      }
      nextPageToken = response.getNextPageToken();
    } while (nextPageToken != null);

    log.debug("Found {} transitive groups for {}", allGroups.size(), email);
    Groups groups = new Groups();
    groups.setGroups(allGroups.isEmpty() ? null : allGroups);
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

  @VisibleForTesting
  protected synchronized GoogleCredentials getGoogleCredential() {
    if (googleCredentials != null) {
      return googleCredentials;
    }
    try {
      Collection<String> scopes = new ArrayList<>();
      scopes.add(DIRECTORY_SCOPE);
      if (config.isExpandIndirectGroups()) {
        scopes.add(CLOUD_IDENTITY_SCOPE);
      }

      googleCredentials = credentialsFactory.create(config.getAuth(), scopes);
      return googleCredentials;
    } catch (IOException ioe) {
      throw new IllegalStateException("Unable to initialize Google Directory credentials", ioe);
    }
  }

  private Directory getDirectoryService() {
    GsonFactory jsonFactory = new GsonFactory();
    GoogleCredentials credentials = getGoogleCredential();
    return new Directory.Builder(
            httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
        .setApplicationName("Spinnaker-Fiat")
        .build();
  }

  @VisibleForTesting
  protected CloudIdentity getCloudIdentityService() {
    GsonFactory jsonFactory = new GsonFactory();
    GoogleCredentials credentials = getGoogleCredential();
    return new CloudIdentity.Builder(
            httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
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

    private GoogleAuthProperties auth = new GoogleAuthProperties();

    // Binding guards, not aliases: stale configuration must never select a different identity.
    public void setCredentialPath(String ignored) {
      throw GoogleAuthProperties.removedProperty("credentialPath", "use auth.credentialsFile");
    }

    public void setAdminUsername(String ignored) {
      throw GoogleAuthProperties.removedProperty(
          "adminUsername", "configure direct Workspace role assignments; delegation is removed");
    }

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

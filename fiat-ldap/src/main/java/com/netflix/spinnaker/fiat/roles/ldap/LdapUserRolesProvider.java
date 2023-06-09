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

package com.netflix.spinnaker.fiat.roles.ldap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.fiat.config.LdapConfig;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.roles.UserRolesProvider;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.control.PagedResultsDirContextProcessor;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.support.LdapEncoder;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.security.ldap.LdapUtils;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "ldap")
public class LdapUserRolesProvider implements UserRolesProvider {

  @Autowired @Setter private SpringSecurityLdapTemplate ldapTemplate;

  @Autowired @Setter private LdapConfig.ConfigProps configProps;

  @Override
  public List<Role> loadRoles(ExternalUser user) {
    String userId = user.getId();

    log.debug("loadRoles for user " + userId);
    if (StringUtils.isEmpty(configProps.getGroupSearchBase())) {
      return new ArrayList<>();
    }

    String fullUserDn = getUserFullDn(userId);

    if (fullUserDn == null) {
      // Likely a service account
      log.debug("fullUserDn is null for {}", userId);
      return new ArrayList<>();
    }

    String[] params = new String[] {fullUserDn, userId};

    if (log.isDebugEnabled()) {
      log.debug(
          new StringBuilder("Searching for groups using ")
              .append("\ngroupSearchBase: ")
              .append(configProps.getGroupSearchBase())
              .append("\ngroupSearchFilter: ")
              .append(configProps.getGroupSearchFilter())
              .append("\nparams: ")
              .append(StringUtils.join(params, " :: "))
              .append("\ngroupRoleAttributes: ")
              .append(configProps.getGroupRoleAttributes())
              .toString());
    }

    // Copied from org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator.
    Set<String> userRoles =
        ldapTemplate.searchForSingleAttributeValues(
            configProps.getGroupSearchBase(),
            configProps.getGroupSearchFilter(),
            params,
            configProps.getGroupRoleAttributes());

    log.debug("Got roles for user " + userId + ": " + userRoles);
    return userRoles.stream()
        .map(role -> new Role(role).setSource(Role.Source.LDAP))
        .collect(Collectors.toList());
  }

  class UserGroupMapper implements AttributesMapper<List<Pair<String, Role>>> {

    @Override
    public List<Pair<String, Role>> mapFromAttributes(Attributes attrs) throws NamingException {
      String group = attrs.get(configProps.getGroupRoleAttributes()).get().toString();
      Role role = new Role(group).setSource(Role.Source.LDAP);
      List<Pair<String, Role>> members = new ArrayList<>();
      log.debug(
          "Parsing out members of the LDAP group {} using attributes {}",
          group,
          configProps.getGroupUserAttributes());
      for (NamingEnumeration<?> groupMembers =
              attrs.get(configProps.getGroupUserAttributes()).getAll();
          groupMembers.hasMore(); ) {
        try {
          String user = getUser(groupMembers.next().toString());
          members.add(Pair.of(user, role));
          log.trace("Found user {} for the ldap group {}", user, group);
        } catch (ParseException e) {
          e.printStackTrace();
        }
      }
      log.debug("Found following member role pairs for the group {}: {}", group, members);
      return members;
    }

    private String getUser(String member) throws ParseException {
      if (configProps.isEnableDnBasedMultiLoad()) {
        return member.toLowerCase();
      } else {
        return String.valueOf(configProps.getUserDnPattern().parse(member)[0]);
      }
    }
  }

  /** Mapper for mapping user distinguished names to their ids */
  class UserDNMapper implements ContextMapper<Pair<String, String>> {

    @Override
    public Pair<String, String> mapFromContext(Object ctx) {
      DirContextAdapter context = (DirContextAdapter) ctx;
      String userDN =
          LdapNameBuilder.newInstance(LdapUtils.parseRootDnFromUrl(configProps.getUrl()))
              .add(context.getDn())
              .build()
              .toString()
              .toLowerCase();
      String userId = context.getStringAttribute(configProps.getUserIdAttribute()).toLowerCase();
      log.trace("Fetched user DN {} for user id {}", userDN, userId);
      return Pair.of(userDN, userId);
    }
  }

  @Override
  public Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users) {
    if (StringUtils.isEmpty(configProps.getGroupSearchBase())) {
      return new HashMap<>();
    }

    if (users.size() > configProps.getThresholdToUseGroupMembership()
        && StringUtils.isNotEmpty(configProps.getGroupUserAttributes())) {
      log.info("Querying all groups to get a mapping of user to its roles.");
      if (configProps.isEnableDnBasedMultiLoad()) {
        return multiLoadDnBasedRoles(users);
      }
      Set<String> userIds = users.stream().map(ExternalUser::getId).collect(Collectors.toSet());
      return ldapTemplate
          .search(
              configProps.getGroupSearchBase(),
              MessageFormat.format(
                  configProps.getGroupSearchFilter(),
                  "*",
                  "*"), // Passing two wildcard params like loadRoles
              new UserGroupMapper())
          .stream()
          .flatMap(List::stream)
          .filter(p -> userIds.contains(p.getKey()))
          .collect(
              Collectors.groupingBy(
                  Pair::getKey,
                  Collectors.mapping(Pair::getValue, Collectors.toCollection(ArrayList::new))));
    }

    log.info("Querying individual groups memberships for {} users", users.size());
    // ExternalUser is used here as a simple data type to hold the username/roles combination.
    return users.stream()
        .map(u -> new ExternalUser().setId(u.getId()).setExternalRoles(loadRoles(u)))
        .collect(Collectors.toMap(ExternalUser::getId, ExternalUser::getExternalRoles));
  }

  private Map<String, Collection<Role>> multiLoadDnBasedRoles(Collection<ExternalUser> users) {
    Set<String> userIds =
        users.stream().map(user -> user.getId().toLowerCase()).collect(Collectors.toSet());
    Map<String, String> userDNToId = getUserDNs(userIds);
    Map<String, Collection<Role>> userDNtoRoles =
        configProps.isEnablePagingForGroupMembershipQueries()
            ? doMultiLoadRolesPaginated(userDNToId.keySet())
            : doMultiLoadRoles(userDNToId.keySet());

    // Convert the fetched roles to a map of user id to roles
    // and if one user has multiple DNs, merge roles
    Map<String, Collection<Role>> rolesForUsers = new HashMap<>();
    userDNtoRoles
        .keySet()
        .forEach(
            userId ->
                rolesForUsers.merge(
                    userDNToId.get(userId),
                    userDNtoRoles.get(userId),
                    (addedRoles, newRoles) ->
                        new ArrayList<>(
                            new HashSet<>() {
                              {
                                addAll(addedRoles);
                                addAll(newRoles);
                              }
                            })));
    return rolesForUsers;
  }

  @VisibleForTesting
  String getUserFullDn(String userId) {
    String rootDn = LdapUtils.parseRootDnFromUrl(configProps.getUrl());
    DistinguishedName root = new DistinguishedName(rootDn);
    log.debug("Root DN: " + root.toString());

    String[] formatArgs = new String[] {LdapEncoder.nameEncode(userId)};

    String partialUserDn;
    if (!StringUtils.isEmpty(configProps.getUserSearchFilter())) {
      try {
        DirContextOperations res =
            ldapTemplate.searchForSingleEntry(
                configProps.getUserSearchBase(), configProps.getUserSearchFilter(), formatArgs);
        partialUserDn = res.getDn().toString();
      } catch (IncorrectResultSizeDataAccessException e) {
        log.error("Unable to find a single user entry for {}", userId, e);
        return null;
      }
    } else {
      partialUserDn = configProps.getUserDnPattern().format(formatArgs);
    }

    DistinguishedName user = new DistinguishedName(partialUserDn);
    log.debug("User portion: " + user.toString());

    try {
      Name fullUser = root.addAll(user);
      log.debug("Full user DN: " + fullUser.toString());
      return fullUser.toString();
    } catch (InvalidNameException ine) {
      log.error("Could not assemble full userDn", ine);
    }
    return null;
  }

  /**
   * Gets the Distinguished Names for the provided user ids using batched ldap queries
   *
   * @param userIds list of user ids to fetch the DNs for
   * @return mapping of user ids to their DNs
   */
  @VisibleForTesting
  Map<String, String> getUserDNs(Collection<String> userIds) {
    log.info("Loading distinguished names for {} users", userIds.size());
    log.debug("Fetching distinguished names for the following users: {}", userIds);
    Map<String, String> userDNToIdMap = new HashMap<>();
    UserDNMapper userDNIdMapper = new UserDNMapper();

    if (StringUtils.isNotEmpty(configProps.getUserSearchFilter())) {
      log.debug(
          "Fetching user DNs from LDAP since user search filter is set to {}",
          configProps.getUserSearchFilter());
      // Partion the list of userIds into batches of fixed sizes and process the batches one at a
      // time
      Iterables.partition(userIds, configProps.getLoadUserDNsBatchSize())
          .forEach(
              userIdsInBatch -> {
                log.debug("Processing the following batch of users: {}", userIdsInBatch);
                List<String> idFilters =
                    userIdsInBatch.stream()
                        .map(
                            userId ->
                                MessageFormat.format(configProps.getUserSearchFilter(), userId))
                        .collect(Collectors.toList());

                // This creates an "OR" filter of this form:
                // (|(employeeEmail=foo@mycompany.com)(employeeEmail=bar@mycompany.com)(employeeEmail=bax@mycompany.com)...)
                String userDNsFilter = String.format("(|%s)", String.join("", idFilters));
                log.trace("LDAP query filter used for fetching the DNs: {}", userDNsFilter);
                List<Pair<String, String>> userDNIdPairs =
                    ldapTemplate.search(
                        configProps.getUserSearchBase(), userDNsFilter, userDNIdMapper);

                log.trace("Fetched the following user id DN pairs from LDAP: {}", userDNIdPairs);
                userDNIdPairs.forEach(pair -> userDNToIdMap.put(pair.getKey(), pair.getValue()));
              });
    } else {
      log.debug("Building user DN from LDAP since user search filter is empty");
      userIds.forEach(userId -> userDNToIdMap.put(getUserFullDn(userId), userId));
    }

    log.debug("Loaded {} user DNs", userDNToIdMap.size());
    return userDNToIdMap;
  }

  @VisibleForTesting
  Map<String, Collection<Role>> doMultiLoadRoles(Collection<String> userIds) {
    log.info("Multi-loading LDAP roles for {} users", userIds.size());
    log.debug("Multi-loading LDAP roles for following users: {}", userIds);
    Map<String, Collection<Role>> userRolesMap =
        ldapTemplate
            .search(
                configProps.getGroupSearchBase(),
                MessageFormat.format(
                    configProps.getGroupSearchFilter(),
                    "*",
                    "*"), // Passing two wildcard params like loadRoles
                new UserGroupMapper())
            .stream()
            .flatMap(List::stream)
            .filter(p -> userIds.contains(p.getKey()))
            .collect(
                Collectors.groupingBy(
                    Pair::getKey,
                    Collectors.mapping(Pair::getValue, Collectors.toCollection(ArrayList::new))));
    log.trace("Loaded the following {} user role mappings: {}", userRolesMap.size(), userRolesMap);
    log.info("Multi-loaded roles for {} users", userRolesMap.size());
    return userRolesMap;
  }

  @VisibleForTesting
  Map<String, Collection<Role>> doMultiLoadRolesPaginated(Collection<String> userIds) {
    log.info(
        "Multi-loading LDAP roles for {} users having pagination enabled with a page size of {}",
        userIds.size(),
        configProps.getPageSizeForGroupMembershipQueries());
    log.debug("Multi-loading LDAP roles for following users using pagination: {}", userIds);
    PagedResultsDirContextProcessor processor =
        getPagedResultsDirContextProcessor(configProps.getPageSizeForGroupMembershipQueries());
    SearchControls searchControls = new SearchControls();
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    int page = 1;
    Map<String, Collection<Role>> userRolesMap = new HashMap<>();
    do {
      log.debug("Processing page {} when querying ldap groups", page);

      Map<String, Collection<Role>> currentPageUsers =
          ldapTemplate
              .search(
                  configProps.getGroupSearchBase(),
                  MessageFormat.format(
                      configProps.getGroupSearchFilter(),
                      "*",
                      "*"), // Passing two wildcard params like loadRoles
                  searchControls,
                  new UserGroupMapper(),
                  processor)
              .stream()
              .flatMap(List::stream)
              .filter(p -> userIds.contains(p.getKey()))
              .collect(
                  Collectors.groupingBy(
                      Pair::getKey,
                      Collectors.mapping(Pair::getValue, Collectors.toCollection(ArrayList::new))));

      log.trace(
          "Loaded the following {} user role mappings in page {}: {}",
          currentPageUsers.size(),
          page,
          currentPageUsers);

      // Add the loaded roles in the final result map
      currentPageUsers.forEach(
          (id, roles) -> userRolesMap.computeIfAbsent(id, k -> new ArrayList<>()).addAll(roles));

      page++;
    } while (processor.hasMore());
    log.trace("Loaded the following {} user role mappings: {}", userRolesMap.size(), userRolesMap);
    log.info("Multi-loaded roles for {} users using pagination", userRolesMap.size());
    return userRolesMap;
  }

  /**
   * Provides a new instance of the {@link PagedResultsDirContextProcessor} class initialized with
   * the provided page size
   */
  @VisibleForTesting
  PagedResultsDirContextProcessor getPagedResultsDirContextProcessor(int pageSize) {
    return new PagedResultsDirContextProcessor(pageSize, null);
  }
}

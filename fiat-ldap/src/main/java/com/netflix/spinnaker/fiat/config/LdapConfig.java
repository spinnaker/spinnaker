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

package com.netflix.spinnaker.fiat.config;

import java.text.MessageFormat;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;

@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "ldap")
public class LdapConfig {

  @Autowired ConfigProps configProps;

  @Bean
  SpringSecurityLdapTemplate springSecurityLdapTemplate() throws Exception {
    DefaultSpringSecurityContextSource contextSource =
        new DefaultSpringSecurityContextSource(configProps.url);
    contextSource.setUserDn(configProps.managerDn);
    contextSource.setPassword(configProps.managerPassword);
    contextSource.afterPropertiesSet();

    return new SpringSecurityLdapTemplate(contextSource);
  }

  @Data
  @Configuration
  @ConfigurationProperties("auth.group-membership.ldap")
  public static class ConfigProps {
    String url;
    String managerDn;
    String managerPassword;

    /** Search base to be used when querying users Example: "ou=users" */
    String userSearchBase = "";

    /** Search base to be used when querying groups Example: "ou=groups" */
    String groupSearchBase = "";

    /** Pattern used for fetching user distinguished names */
    MessageFormat userDnPattern = new MessageFormat("uid={0},ou=users");

    /** Search filter used for querying users' distinguished names Example: "(employeeEmail={0})" */
    String userSearchFilter;

    /** Search filter used for querying groups */
    String groupSearchFilter = "(uniqueMember={0})";

    /** Group attribute for parsing out the role from the fetched groups */
    String groupRoleAttributes = "cn";

    /**
     * Group attribute for parsing out the group members from the fetched groups Example: "member"
     */
    String groupUserAttributes = "";

    /**
     * Controls the user count threshold, used for determining if ldap groups for each user should
     * be queried individually or not. If the threshold is breached, LDAP is queried to retrieve all
     * groups and their members and then filtered based on the provided users
     */
    int thresholdToUseGroupMembership = 100;

    /**
     * Controls whether paging should be used when fetching all LDAP groups. This is only applicable
     * when enableDnBasedMultiLoad is true and thresholdToUseGroupMembership is breached.
     */
    boolean enablePagingForGroupMembershipQueries;

    /**
     * Number of results fetched per page for group membership queries. This is only applicable when
     * thresholdToUseGroupMembership is breached and enablePagingForGroupMembershipQueries is set to
     * true.
     */
    int pageSizeForGroupMembershipQueries = 100;

    /**
     * This value is used to determine the number of users to include in every ldap query when
     * fetching user DNs from LDAP
     */
    int loadUserDNsBatchSize = 100;

    /**
     * The attribute used for parsing the id of the fetched ldap user. This is the id provided to
     * Fiat when logging in the user, eg. employee email. This attribute is used for creating a map
     * of the user dn to user id.
     */
    String userIdAttribute = "employeeEmail";

    /**
     * This attribute if true, enables multi loading of roles based on user DNs fetched using
     * batched ldap queries
     */
    boolean enableDnBasedMultiLoad = false;

    /**
     * Configures if caching of LDAP responses is enabled, and if so, the cache specific settings.
     */
    @NestedConfigurationProperty
    private UserRolesProviderCacheConfig cache = new UserRolesProviderCacheConfig();
  }
}

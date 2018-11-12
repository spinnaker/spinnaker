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

package com.netflix.spinnaker.gate.security.ldap

import com.netflix.spinnaker.gate.security.MultiAuthConfigurer
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.config.AuthConfig
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.security.SuppportsMultiAuth
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.security.User
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.ldap.core.DirContextAdapter
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper
import org.springframework.stereotype.Component

@ConditionalOnExpression('${ldap.enabled:false}')
@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
@SuppportsMultiAuth
@Order(Ordered.LOWEST_PRECEDENCE)
class LdapSsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  AuthConfig authConfig

  @Autowired
  LdapConfigProps ldapConfigProps

  @Autowired
  LdapUserContextMapper ldapUserContextMapper

  @Autowired(required = false)
  List<MultiAuthConfigurer> additionalAuthProviders

  @Autowired
  SecurityProperties securityProperties

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {

    // In order for the HttpBasic user to have its creds checked first (and to keep unnecessary calls
    // from hitting the LDAP server), this must be configured before the full LDAP config.
    // See https://github.com/spinnaker/spinnaker/issues/3589
    if (securityProperties.basic.enabled) {
      auth.inMemoryAuthentication()
          .withUser(securityProperties.user.name)
          .password(securityProperties.user.password)
          .roles("USER")
    }

    def ldapConfigurer =
        auth.ldapAuthentication()
            .contextSource()
              .url(ldapConfigProps.url)
              .managerDn(ldapConfigProps.managerDn)
              .managerPassword(ldapConfigProps.managerPassword)
            .and()
            .rolePrefix("")
            .groupSearchBase(ldapConfigProps.groupSearchBase)
            .userDetailsContextMapper(ldapUserContextMapper)

    if (ldapConfigProps.userDnPattern) {
      ldapConfigurer.userDnPatterns(ldapConfigProps.userDnPattern)
    }

    if (ldapConfigProps.userSearchBase) {
      ldapConfigurer.userSearchBase(ldapConfigProps.userSearchBase)
    }

    if (ldapConfigProps.userSearchFilter) {
      ldapConfigurer.userSearchFilter(ldapConfigProps.userSearchFilter)
    }
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.formLogin()
    authConfig.configure(http)
    additionalAuthProviders?.each {
      it.configure(http)
    }
  }

  @Override
  void configure(WebSecurity web) throws Exception {
    authConfig.configure(web)
  }

  @Component
  static class LdapUserContextMapper implements UserDetailsContextMapper {

    @Autowired
    PermissionService permissionService

    @Autowired
    AllowedAccountsSupport allowedAccountsSupport

    @Override
    UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
      def roles = sanitizeRoles(authorities)
      permissionService.loginWithRoles(username, roles)

      return new User(username: username,
                      email: ctx.getStringAttribute("mail"),
                      roles: roles,
                      allowedAccounts: allowedAccountsSupport.filterAllowedAccounts(username, roles))
    }

    @Override
    void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
      throw new UnsupportedOperationException("Cannot save to LDAP server")
    }

    private static Set<String> sanitizeRoles(Collection<? extends GrantedAuthority> authorities) {
      authorities.findResults {
        StringUtils.removeStartIgnoreCase(it.authority, "ROLE_")?.toLowerCase()
      }
    }
  }

  @Component
  @ConfigurationProperties("ldap")
  static class LdapConfigProps {
    String url
    String managerDn
    String managerPassword
    String groupSearchBase

    String userDnPattern
    String userSearchBase
    String userSearchFilter
  }
}

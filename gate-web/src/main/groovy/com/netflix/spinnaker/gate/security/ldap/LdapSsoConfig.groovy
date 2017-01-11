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

import com.netflix.spinnaker.gate.security.AuthConfig
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.security.User
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.ldap.core.DirContextAdapter
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper
import org.springframework.stereotype.Component

@ConditionalOnExpression('${ldap.enabled:false}')
@Configuration
@SpinnakerAuthConfig
@EnableWebMvcSecurity
@Import(SecurityAutoConfiguration)
class LdapSsoConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  AuthConfig authConfig

  @Autowired
  LdapConfigProps ldapConfigProps

  @Autowired
  LdapUserContextMapper ldapUserContextMapper

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
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
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    authConfig.configure(http)
    http.formLogin()
  }

  @Component
  static class LdapUserContextMapper implements UserDetailsContextMapper {

    @Autowired
    PermissionService permissionService

    @Override
    UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
      def roles = sanitizeRoles(authorities)
      permissionService.loginSAML(username, roles)

      return new User(username: username,
                      email: ctx.getStringAttribute("mail"),
                      roles: roles).asImmutable()
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
  }
}

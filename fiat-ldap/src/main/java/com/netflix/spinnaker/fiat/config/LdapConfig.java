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

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.SpringSecurityLdapTemplate;

import java.text.MessageFormat;

@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "ldap")
public class LdapConfig {

  @Autowired
  ConfigProps configProps;

  @Bean
  SpringSecurityLdapTemplate springSecurityLdapTemplate() throws Exception {
    DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(configProps.url);
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

    String groupSearchBase = "";
    MessageFormat userDnPattern = new MessageFormat("uid={0},ou=users");
    String userSearchBase = "";
    String userSearchFilter;
    String groupSearchFilter = "(uniqueMember={0})";
    String groupRoleAttributes = "cn";
  }
}

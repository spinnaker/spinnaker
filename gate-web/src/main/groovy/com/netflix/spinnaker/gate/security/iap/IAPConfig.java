/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.gate.security.iap;

import com.google.common.base.Preconditions;
import com.netflix.spinnaker.gate.security.AuthConfig;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import com.netflix.spinnaker.gate.security.iap.IAPConfig.IAPSecurityConfigProperties;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * This Web Security configuration supports the Google Cloud Identity-Aware Proxy authentication
 * model.
 */
@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
@ConditionalOnExpression("${security.iap.enabled:false}")
@EnableConfigurationProperties(IAPSecurityConfigProperties.class)
public class IAPConfig extends WebSecurityConfigurerAdapter {

  @Autowired
  AuthConfig authConfig;

  @Autowired
  PermissionService permissionService;

  @Autowired
  Front50Service front50Service;

  @Autowired
  IAPSecurityConfigProperties configProperties;

  @ConfigurationProperties("security.iap")
  @Data
  public static class IAPSecurityConfigProperties {

    String jwtHeader = "x-goog-iap-jwt-assertion";
    String issuerId = "https://cloud.google.com/iap";
    String audience;
    String iapVerifyKeyUrl = "https://www.gstatic.com/iap/verify/public_key-jwk";
  }

  private final Logger logger = LoggerFactory.getLogger(IAPConfig.class);

  @Override
  public void configure(HttpSecurity http) throws Exception {
    logger.info("IAP JWT token verification is enabled.");

    Preconditions.checkNotNull(configProperties.getAudience(), "Please set the "
      + "Audience field. You can retrieve this field from the IAP console: "
      + "https://cloud.google.com/iap/docs/signed-headers-howto#verify_the_id_token_header.");

    authConfig.configure(http);

    IAPAuthenticationFilter authFilter = new IAPAuthenticationFilter(
      configProperties, permissionService, front50Service);

    http.addFilterBefore(authFilter, BasicAuthenticationFilter.class);
  }
}

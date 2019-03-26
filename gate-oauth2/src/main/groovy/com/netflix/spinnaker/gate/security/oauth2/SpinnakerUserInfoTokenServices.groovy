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

package com.netflix.spinnaker.gate.security.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport
import com.netflix.spinnaker.gate.security.oauth2.provider.SpinnakerProviderTokenServices
import com.netflix.spinnaker.gate.services.CredentialsService
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoTokenServices
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.OAuth2Request
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import retrofit.RetrofitError

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

import static net.logstash.logback.argument.StructuredArguments.*

/**
 * ResourceServerTokenServices is an interface used to manage access tokens. The UserInfoTokenService object is an
 * implementation of that interface that uses an access token to get the logged in user's data (such as email or
 * profile). We want to customize the Authentication object that is returned to include our custom (Kork) User.
 */
@Slf4j
class SpinnakerUserInfoTokenServices implements ResourceServerTokenServices {
  @Autowired
  ResourceServerProperties sso

  @Autowired
  UserInfoTokenServices userInfoTokenServices

  @Autowired
  CredentialsService credentialsService

  @Autowired
  protected OAuth2SsoConfig.UserInfoMapping userInfoMapping

  @Autowired
  OAuth2SsoConfig.UserInfoRequirements userInfoRequirements

  @Autowired
  PermissionService permissionService

  @Autowired
  Front50Service front50Service

  @Autowired(required = false)
  SpinnakerProviderTokenServices providerTokenServices

  @Autowired
  AllowedAccountsSupport allowedAccountsSupport

  @Override
  OAuth2Authentication loadAuthentication(String accessToken) throws AuthenticationException, InvalidTokenException {
    OAuth2Authentication oAuth2Authentication = userInfoTokenServices.loadAuthentication(accessToken)

    Map details = oAuth2Authentication.userAuthentication.details as Map

    if (log.isDebugEnabled()) {
      log.debug("UserInfo details: " + entries(details))
    }

    def isServiceAccount = isServiceAccount(details)
    if (!isServiceAccount) {
      if (!hasAllUserInfoRequirements(details)) {
        throw new BadCredentialsException("User's info does not have all required fields.")
      }
      if (providerTokenServices != null && !providerTokenServices.hasAllProviderRequirements(accessToken, details)) {
        throw new BadCredentialsException("User's provider info does not have all required fields.")
      }
    }

    def username = details[userInfoMapping.username] as String
    def roles = getRoles(details)

    // Service accounts are already logged in.
    if (!isServiceAccount) {
      if (roles.isEmpty()) {
        permissionService.login(username)
      } else {
        permissionService.loginWithRoles(username, roles)
      }
    }

    User spinnakerUser = new User(
        email: details[userInfoMapping.email] as String,
        firstName: details[userInfoMapping.firstName] as String,
        lastName: details[userInfoMapping.lastName] as String,
        allowedAccounts: allowedAccountsSupport.filterAllowedAccounts(username, roles),
        roles: roles,
        username: username)

    PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
        spinnakerUser,
        null /* credentials */,
        spinnakerUser.authorities
    )

    // impl copied from UserInfoTokenServices
    OAuth2Request storedRequest = new OAuth2Request(null, sso.clientId, null, true /*approved*/,
                                                    null, null, null, null, null);

    return new OAuth2Authentication(storedRequest, authentication)
  }

  @Override
  OAuth2AccessToken readAccessToken(String accessToken) {
    return userInfoTokenServices.readAccessToken(accessToken)
  }

  boolean isServiceAccount(Map details) {
    String email = details[userInfoMapping.serviceAccountEmail]
    if (!email || !permissionService.isEnabled()) {
      return false
    }
    try {
      def serviceAccounts = front50Service.getServiceAccounts()
      return serviceAccounts.find { email.equalsIgnoreCase(it.name) }
    } catch (RetrofitError re) {
      log.warn("Could not get list of service accounts.", re)
    }
    return false
  }

  boolean hasAllUserInfoRequirements(Map details) {
    if (!userInfoRequirements) {
      return true
    }

    def invalidFields = userInfoRequirements.findAll { String reqKey, String reqVal ->
      if (details[reqKey] && isRegexExpression(reqVal)) {
        return !String.valueOf(details[reqKey]).matches(mutateRegexPattern(reqVal))
      }
      return details[reqKey] != reqVal
    }
    if (invalidFields && log.debugEnabled) {
      log.debug "Invalid userInfo response: " + invalidFields.collect({k, v -> "got $k=${details[k]}, wanted $v"}).join(", ")
    }

    return !invalidFields
  }

  static boolean isRegexExpression(String val) {
    if (val.startsWith('/') && val.endsWith('/')) {
      try {
        Pattern.compile(val)
        return true
      } catch (PatternSyntaxException ignored) {
        return false
      }
    }
    return false
  }

  static String mutateRegexPattern(String val) {
    // "/expr/" -> "expr"
    val.substring(1, val.length() - 1)
  }

  protected List<String> getRoles(Map<String, String> details) {
    return []
  }
}
